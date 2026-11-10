package com.auraalarm.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.auraalarm.R

/**
 * Sonidos cortos del reto: un "ding" por repetición y un arpegio al completarlo.
 * Van por el canal de alarma, así se oyen por encima de la alarma (que baja mientras haces el ejercicio).
 */
class RepSounds(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val repId = pool.load(context, R.raw.rep_ding, 1)
    private val completeId = pool.load(context, R.raw.challenge_complete, 1)

    fun playRep() {
        pool.play(repId, REP_VOLUME, REP_VOLUME, 1, 0, 1f)
    }

    fun playComplete() {
        pool.play(completeId, 1f, 1f, 2, 0, 1f)
    }

    fun release() = pool.release()

    private companion object {
        const val REP_VOLUME = 0.8f
    }
}
