package com.auraalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.auraalarm.data.Alarm
import com.auraalarm.data.AlarmStore
import com.auraalarm.ui.MainActivity
import java.time.ZonedDateTime

/**
 * Todo se programa con AlarmManager.setAlarmClock: es el único modo que Android (y MIUI)
 * respetan siempre, incluso en Doze, y muestra el icono de alarma en la barra de estado.
 */
object AlarmScheduler {
    const val ACTION_FIRE = "com.auraalarm.action.FIRE"
    const val ACTION_WATCHDOG = "com.auraalarm.action.WATCHDOG"
    const val EXTRA_ALARM_ID = "alarm_id"

    private fun alarmManager(context: Context) = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager(context).canScheduleExactAlarms()

    /** Cancela y vuelve a programar todas las alarmas activas. */
    fun rescheduleAll(context: Context) {
        val alarms = AlarmStore.all(context)
        alarms.forEach { cancel(context, it.id) }
        alarms.filter { it.enabled }.forEach { schedule(context, it) }
    }

    fun schedule(context: Context, alarm: Alarm) {
        val at = alarm.nextTrigger(ZonedDateTime.now()).toInstant().toEpochMilli()
        setExact(context, at, firePendingIntent(context, alarm.id))
    }

    fun cancel(context: Context, alarmId: Int) {
        alarmManager(context).cancel(firePendingIntent(context, alarmId))
    }

    /** Próxima alarma activa y cuándo sonará. */
    fun nextAlarm(alarms: List<Alarm>, now: ZonedDateTime = ZonedDateTime.now()): Pair<Alarm, ZonedDateTime>? =
        alarms.filter { it.enabled }
            .map { it to it.nextTrigger(now) }
            .minByOrNull { it.second }

    /**
     * Vigilante: mientras la alarma no se apague con el ejercicio, cada minuto se comprueba que
     * siga sonando. Si MIUI mata la app (p. ej. al "limpiar" recientes), vuelve a sonar.
     */
    fun scheduleWatchdog(context: Context, delayMs: Long) {
        setExact(context, System.currentTimeMillis() + delayMs, watchdogPendingIntent(context))
    }

    fun cancelWatchdog(context: Context) {
        alarmManager(context).cancel(watchdogPendingIntent(context))
    }

    fun setExact(context: Context, triggerAtMs: Long, operation: PendingIntent) {
        val am = alarmManager(context)
        if (canScheduleExact(context)) {
            val show = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMs, show), operation)
        } else {
            // Sin permiso de alarma exacta (solo posible en Android 12): mejor tarde que nunca.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, operation)
        }
    }

    private fun firePendingIntent(context: Context, alarmId: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            .setData(Uri.parse("auraalarm://alarm/$alarmId"))
            .putExtra(EXTRA_ALARM_ID, alarmId)
        return PendingIntent.getBroadcast(
            context, alarmId, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun watchdogPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_WATCHDOG)
            .setData(Uri.parse("auraalarm://watchdog"))
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
