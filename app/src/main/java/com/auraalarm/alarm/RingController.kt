package com.auraalarm.alarm

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.auraalarm.data.HistoryStore
import com.auraalarm.data.RingSession
import com.auraalarm.data.SessionStore
import com.auraalarm.data.SettingsStore
import com.auraalarm.data.WakeEntry
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Ciclo de vida de una alarma sonando. Modo sin escape: solo [complete] (llamado por la pantalla
 * del reto al terminar las repeticiones) detiene el sonido.
 */
object RingController {
    private const val WATCHDOG_INTERVAL_MS = 60_000L

    /** La pantalla del reto baja el volumen mientras te ve haciendo el ejercicio. */
    val ducked = MutableStateFlow(false)

    fun startRinging(context: Context, session: RingSession) {
        if (SessionStore.get(context) != null) return // ya hay una alarma sonando
        SessionStore.set(context, session)
        ensureRinging(context)
    }

    /** Arranca (o confirma) el servicio que suena y reprograma el vigilante. */
    fun ensureRinging(context: Context) {
        if (SessionStore.get(context) == null) return
        AlarmScheduler.scheduleWatchdog(context, WATCHDOG_INTERVAL_MS)
        // Si el sistema no deja arrancar el servicio ahora, el vigilante lo reintenta en un minuto.
        runCatching { ContextCompat.startForegroundService(context, Intent(context, AlarmService::class.java)) }
    }

    fun complete(context: Context) {
        val session = SessionStore.get(context) ?: return
        SessionStore.clear(context)
        AlarmScheduler.cancelWatchdog(context)
        ducked.value = false
        context.stopService(Intent(context, AlarmService::class.java))

        val now = System.currentTimeMillis()
        val checkEnabled = SettingsStore.awakeCheckEnabled(context)
        val entry = WakeEntry(
            id = now,
            alarmId = session.alarmId,
            exercise = session.exercise,
            reps = session.reps,
            ringStartedAt = session.startedAt,
            dismissedAt = now,
            isRecheck = session.isRecheck,
            awake = null,
        )
        HistoryStore.add(context, entry)
        if (checkEnabled) AwakeCheck.schedule(context, entry, session)
    }
}
