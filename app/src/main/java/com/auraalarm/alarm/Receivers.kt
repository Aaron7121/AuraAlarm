package com.auraalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.auraalarm.data.AlarmStore
import com.auraalarm.data.RingSession
import com.auraalarm.data.SessionStore

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AlarmScheduler.ACTION_FIRE -> onFire(context, intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1))
            AlarmScheduler.ACTION_WATCHDOG -> RingController.ensureRinging(context)
        }
    }

    private fun onFire(context: Context, alarmId: Int) {
        val alarm = AlarmStore.get(context, alarmId)
        if (alarm == null || !alarm.enabled) {
            AlarmScheduler.rescheduleAll(context)
            return
        }
        if (!alarm.isRepeating) AlarmStore.upsert(context, alarm.copy(enabled = false))
        AlarmScheduler.rescheduleAll(context)

        RingController.startRinging(
            context,
            RingSession(
                alarmId = alarm.id,
                label = alarm.label,
                exercise = alarm.exercise.resolve(),
                reps = alarm.reps,
                startedAt = System.currentTimeMillis(),
                isRecheck = false,
                soundUri = alarm.soundUri,
            ),
        )
    }
}

/** Reprograma todo tras reiniciar, actualizar la app o cambiar la hora del sistema. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        AlarmScheduler.rescheduleAll(context)

        // Sin escape: si apagaste el teléfono con la alarma sonando, vuelve a sonar al encender.
        // Se descarta si ha pasado demasiado tiempo (p. ej. el teléfono se quedó sin batería todo el día).
        val session = SessionStore.get(context)
        if (session != null) {
            if (System.currentTimeMillis() - session.startedAt < RESTORE_WINDOW_MS) {
                // Se arranca desde una alarma exacta (y no directamente aquí) porque Android 15
                // no deja iniciar un servicio de reproducción desde BOOT_COMPLETED.
                AlarmScheduler.scheduleWatchdog(context, 5_000)
            } else {
                SessionStore.clear(context)
            }
        }
        AwakeCheck.restoreAfterBoot(context)
    }

    companion object {
        const val RESTORE_WINDOW_MS = 3 * 60 * 60 * 1000L
        private val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}

class AwakeCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AwakeCheck.ACTION_SHOW -> AwakeCheck.onShow(context)
            AwakeCheck.ACTION_CONFIRM -> AwakeCheck.onConfirm(context)
            AwakeCheck.ACTION_DEADLINE -> AwakeCheck.onDeadline(context)
        }
    }
}
