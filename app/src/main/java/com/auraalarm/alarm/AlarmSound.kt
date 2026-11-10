package com.auraalarm.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.core.net.toUri

/** Elección y reproducción del sonido de cada alarma. */
object AlarmSound {

    val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /**
     * Sonidos a intentar, en orden: el elegido y después los del sistema. Si el elegido ya no
     * existe (archivo borrado, permiso perdido), la alarma suena igualmente con el predeterminado.
     */
    fun candidates(context: Context, chosen: String?): List<Uri> = listOfNotNull(
        chosen?.toUri(),
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
    ).distinct()

    /** Crea y arranca un reproductor; null si ese sonido no se puede reproducir. */
    fun play(context: Context, uri: Uri, looping: Boolean): MediaPlayer? {
        val mp = MediaPlayer()
        return runCatching {
            mp.setAudioAttributes(attributes)
            mp.setDataSource(context, uri)
            mp.isLooping = looping
            mp.prepare()
            mp.start()
            mp
        }.getOrElse {
            mp.release()
            null
        }
    }

    /** Reproduce el primer candidato que funcione. Devuelve el reproductor y si se usó el sonido elegido. */
    fun playFirstAvailable(context: Context, chosen: String?, looping: Boolean): Pair<MediaPlayer, Boolean>? {
        val list = candidates(context, chosen)
        list.forEachIndexed { i, uri ->
            play(context, uri, looping)?.let { return it to (chosen != null && i == 0) }
        }
        return null
    }

    fun isSystemDefault(uri: Uri): Boolean =
        uri == Settings.System.DEFAULT_ALARM_ALERT_URI ||
            uri == RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

    /** Tonos del sistema (selector de tonos) frente a archivos propios (selector de archivos). */
    fun isSystemTone(uri: String): Boolean = uri.toUri().authority in setOf("media", "settings")

    fun ringtoneTitle(context: Context, uri: Uri): String? =
        runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }.getOrNull()

    fun fileName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()?.substringBeforeLast('.')
}
