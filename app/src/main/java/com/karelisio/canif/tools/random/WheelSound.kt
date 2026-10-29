package com.karelisio.canif.tools.random

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Synthesized wheel sounds: a short wooden "tick" per sector and a soft two-note chime for the result. */
class WheelSound {
    private val rate = 44100
    private val tick = track(click())
    private val win = track(chime())

    private fun click(): ShortArray {
        val n = (rate * 0.014).toInt()
        val noise = Random(7)
        return ShortArray(n) { i ->
            val t = i.toDouble() / rate
            val env = exp(-t * 420)
            val v = 0.55 * sin(2 * PI * 1900 * t) + 0.3 * sin(2 * PI * 3400 * t) + 0.25 * (noise.nextDouble() * 2 - 1)
            (v * env * 0.5 * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun chime(): ShortArray {
        val n = (rate * 0.45).toInt()
        return ShortArray(n) { i ->
            val t = i.toDouble() / rate
            val a = sin(2 * PI * 784 * t) * exp(-t * 7)
            val b = if (t > 0.09) sin(2 * PI * 1175 * (t - 0.09)) * exp(-(t - 0.09) * 6) else 0.0
            ((a * 0.35 + b * 0.35) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun track(data: ShortArray): AudioTrack? = runCatching {
        AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(data.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
            .also { it.write(data, 0, data.size) }
    }.getOrNull()

    private fun AudioTrack.replay() = runCatching {
        if (playState == AudioTrack.PLAYSTATE_PLAYING) stop()
        reloadStaticData()
        play()
    }

    fun tick() { tick?.replay() }
    fun win() { win?.replay() }

    fun release() {
        tick?.release()
        win?.release()
    }
}
