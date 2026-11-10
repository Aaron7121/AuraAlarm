package com.auraalarm

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager

class AuraApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)

        // La alarma suena desde el servicio, así que el canal va en silencio:
        // solo sirve para la notificación de pantalla completa.
        val ringing = NotificationChannel(CHANNEL_RINGING, "Alarma sonando", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Pantalla completa del reto cuando suena una alarma"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }

        val check = NotificationChannel(CHANNEL_AWAKE_CHECK, "¿Sigues despierto?", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Aviso que debes tocar unos minutos después de apagar la alarma"
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 200, 400)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        nm.createNotificationChannels(listOf(ringing, check))
    }

    companion object {
        const val CHANNEL_RINGING = "ringing"
        const val CHANNEL_AWAKE_CHECK = "awake_check"
    }
}
