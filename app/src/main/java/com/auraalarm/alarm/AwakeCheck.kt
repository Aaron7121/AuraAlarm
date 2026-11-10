package com.auraalarm.alarm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.auraalarm.AuraApp
import com.auraalarm.R
import com.auraalarm.data.AlarmStore
import com.auraalarm.data.AwakeCheckState
import com.auraalarm.data.AwakeCheckStore
import com.auraalarm.data.Exercise
import com.auraalarm.data.HistoryStore
import com.auraalarm.data.RingSession
import com.auraalarm.data.SettingsStore
import com.auraalarm.data.WakeEntry

/**
 * Chequeo anti-dormirse: unos minutos después de apagar la alarma aparece un aviso.
 * Si no lo tocas a tiempo, la alarma vuelve a sonar con un nuevo reto.
 */
object AwakeCheck {
    const val ACTION_SHOW = "com.auraalarm.action.AWAKE_SHOW"
    const val ACTION_CONFIRM = "com.auraalarm.action.AWAKE_CONFIRM"
    const val ACTION_DEADLINE = "com.auraalarm.action.AWAKE_DEADLINE"

    private const val NOTIFICATION_ID = 2002
    const val RESPONSE_WINDOW_MS = 2 * 60_000L
    private const val BOOT_RESTORE_WINDOW_MS = 3 * 60 * 60 * 1000L

    fun schedule(context: Context, entry: WakeEntry, session: RingSession) {
        val delayMs = SettingsStore.awakeCheckDelayMinutes(context) * 60_000L
        val state = AwakeCheckState(
            entryId = entry.id,
            alarmId = session.alarmId,
            label = session.label,
            reps = session.reps,
            checkAt = entry.dismissedAt + delayMs,
            deadlineAt = 0,
        )
        AwakeCheckStore.set(context, state)
        AlarmScheduler.setExact(context, state.checkAt, pendingIntent(context, ACTION_SHOW))
    }

    fun onShow(context: Context) {
        val state = AwakeCheckStore.get(context) ?: return
        val deadline = System.currentTimeMillis() + RESPONSE_WINDOW_MS
        AwakeCheckStore.set(context, state.copy(deadlineAt = deadline))
        AlarmScheduler.setExact(context, deadline, pendingIntent(context, ACTION_DEADLINE))
        postNotification(context)
    }

    fun onConfirm(context: Context) {
        val state = AwakeCheckStore.get(context) ?: return
        cancelPending(context)
        AwakeCheckStore.clear(context)
        HistoryStore.setAwake(context, state.entryId, true)
    }

    fun onDeadline(context: Context) {
        val state = AwakeCheckStore.get(context) ?: return
        cancelPending(context)
        AwakeCheckStore.clear(context)
        HistoryStore.setAwake(context, state.entryId, false)

        val alarm = AlarmStore.get(context, state.alarmId)
        val exercise = alarm?.exercise ?: Exercise.RANDOM
        RingController.startRinging(
            context,
            RingSession(
                alarmId = state.alarmId,
                label = state.label.ifBlank { "¡Te volviste a dormir!" },
                exercise = exercise.resolve(),
                reps = state.reps,
                startedAt = System.currentTimeMillis(),
                isRecheck = true,
                soundUri = alarm?.soundUri,
            ),
        )
    }

    fun restoreAfterBoot(context: Context) {
        val state = AwakeCheckStore.get(context) ?: return
        val now = System.currentTimeMillis()
        if (now - state.checkAt > BOOT_RESTORE_WINDOW_MS) {
            AwakeCheckStore.clear(context)
            return
        }
        if (state.deadlineAt == 0L) {
            AlarmScheduler.setExact(context, maxOf(state.checkAt, now + 5_000), pendingIntent(context, ACTION_SHOW))
        } else {
            // El aviso se perdió con el reinicio: se vuelve a mostrar con un plazo nuevo.
            AlarmScheduler.setExact(context, now + 5_000, pendingIntent(context, ACTION_SHOW))
        }
    }

    private fun cancelPending(context: Context) {
        val am = context.getSystemService(android.app.AlarmManager::class.java)
        am.cancel(pendingIntent(context, ACTION_SHOW))
        am.cancel(pendingIntent(context, ACTION_DEADLINE))
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    private fun postNotification(context: Context) {
        val confirm = pendingIntent(context, ACTION_CONFIRM)
        val minutes = RESPONSE_WINDOW_MS / 60_000
        val notification = NotificationCompat.Builder(context, AuraApp.CHANNEL_AWAKE_CHECK)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("¿Sigues despierto?")
            .setContentText("Toca aquí en menos de $minutes min o la alarma volverá a sonar.")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(true)
            .setTimeoutAfter(RESPONSE_WINDOW_MS)
            .setContentIntent(confirm)
            .addAction(0, "¡Estoy despierto!", confirm)
            .build()
        runCatching {
            context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun pendingIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, AwakeCheckReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("auraalarm://awake/$action"))
        return PendingIntent.getBroadcast(
            context, action.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
