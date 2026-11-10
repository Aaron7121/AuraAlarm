package com.auraalarm.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.auraalarm.AuraApp
import com.auraalarm.R
import com.auraalarm.data.RingSession
import com.auraalarm.data.SessionStore
import com.auraalarm.ui.AlarmActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Hace sonar la alarma hasta que [RingController.complete] la detiene.
 * Fuerza el volumen de alarma al máximo y lo restaura al terminar.
 */
class AlarmService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())

    private var player: MediaPlayer? = null
    private var tone: ToneGenerator? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var savedAlarmVolume: Int = -1
    private var ringing = false

    private val audioManager by lazy { getSystemService(AudioManager::class.java) }

    /** Si bajas el volumen con los botones mientras suena, vuelve a subir. */
    private val volumeEnforcer = object : Runnable {
        override fun run() {
            if (!ringing) return
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            if (audioManager.getStreamVolume(AudioManager.STREAM_ALARM) < max) {
                runCatching { audioManager.setStreamVolume(AudioManager.STREAM_ALARM, max, 0) }
            }
            handler.postDelayed(this, 1_500)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val session = SessionStore.get(this)
        // startForeground debe llamarse siempre tras startForegroundService, incluso si vamos a parar.
        startInForeground(buildNotification(session))
        if (session == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!ringing) startRinging(session)
        launchChallenge()
        return START_STICKY
    }

    override fun onDestroy() {
        stopRinging()
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground(notification: Notification) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
    }

    private fun buildNotification(session: RingSession?): Notification {
        val open = PendingIntent.getActivity(
            this, 1,
            Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = session?.label?.takeIf { it.isNotBlank() } ?: "¡Hora de despertar!"
        val text = session?.let { "Haz ${it.reps} ${it.exercise.plural} para apagarla" } ?: "Preparando la alarma…"
        return NotificationCompat.Builder(this, AuraApp.CHANNEL_RINGING)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /**
     * La notificación de pantalla completa abre el reto con la pantalla bloqueada. Con el teléfono
     * desbloqueado Android solo muestra un aviso, así que además se intenta abrir directamente
     * (funciona si tienes "Mostrar sobre otras apps" o, en Xiaomi, "Ventanas emergentes en segundo plano").
     */
    private fun launchChallenge() {
        runCatching {
            startActivity(
                Intent(this, AlarmActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            )
        }
    }

    private fun startRinging(session: RingSession) {
        ringing = true

        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AuraAlarm:ringing").apply {
            setReferenceCounted(false)
            acquire(2 * 60 * 60 * 1000L)
        }

        savedAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        runCatching {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        }
        handler.post(volumeEnforcer)

        startSound(session.soundUri)
        startVibration()

        scope.launch {
            RingController.ducked.collect { ducked ->
                val v = if (ducked) DUCKED_VOLUME else 1f
                runCatching { player?.setVolume(v, v) }
            }
        }
    }

    private fun startSound(chosen: String?) {
        player = AlarmSound.playFirstAvailable(this, chosen, looping = true)?.first
        if (player != null) return
        // Último recurso: un tono generado que no depende de ningún archivo.
        tone = runCatching {
            ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME).apply {
                startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK)
            }
        }.getOrNull()
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 900, 700), 0)
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        @Suppress("DEPRECATION")
        runCatching { vibrator?.vibrate(effect, attrs) }
    }

    private fun stopRinging() {
        if (!ringing) return
        ringing = false
        handler.removeCallbacks(volumeEnforcer)
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        runCatching { tone?.stopTone() }
        runCatching { tone?.release() }
        tone = null
        runCatching { vibrator?.cancel() }
        if (savedAlarmVolume >= 0) {
            runCatching { audioManager.setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVolume, 0) }
        }
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        /** Volumen mientras la cámara te ve haciendo el ejercicio. */
        private const val DUCKED_VOLUME = 0.2f
    }
}
