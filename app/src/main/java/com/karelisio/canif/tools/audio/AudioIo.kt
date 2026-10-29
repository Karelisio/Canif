package com.karelisio.canif.tools.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Process
import androidx.core.content.ContextCompat
import kotlin.math.max

/**
 * Microphone capture on a dedicated urgent-audio thread. [onBlock] receives a reused buffer of exactly
 * [hop] mono samples in -1..1, on the capture thread (no allocation per block). [onStart] is called
 * once on the capture thread with the actual sample rate, before the first block.
 */
class MicCapture(
    context: Context,
    private val hop: Int,
    private val fallbackSource: Int = MediaRecorder.AudioSource.MIC,
    private val onStart: (sampleRate: Int) -> Unit,
    private val onBlock: (FloatArray) -> Unit,
) {
    private val app = context.applicationContext

    @Volatile private var running = false
    private var thread: Thread? = null

    /** True when the microphone could not be opened (busy, denied, unsupported). */
    @Volatile var failed = false
        private set

    fun start() {
        if (thread?.isAlive == true) return
        failed = false
        running = true
        thread = Thread({ loop() }, "canif-mic").also { it.start() }
    }

    /** Stops capturing; the recorder is released by the capture thread itself. */
    fun stop() {
        running = false
        thread?.join(400)
        thread = null
    }

    private class Opened(val rec: AudioRecord, val float: Boolean)

    @SuppressLint("MissingPermission")
    private fun open(): Opened? {
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return null
        val am = app.getSystemService(AudioManager::class.java)
        val unprocessed = am?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        val sources = if (unprocessed) intArrayOf(MediaRecorder.AudioSource.UNPROCESSED, fallbackSource) else intArrayOf(fallbackSource)
        for (src in sources) for (rate in intArrayOf(48000, 44100)) for (enc in intArrayOf(AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_16BIT)) {
            val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, enc)
            if (min <= 0) continue
            val bytes = if (enc == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
            val rec = try {
                AudioRecord.Builder()
                    .setAudioSource(src)
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(enc).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                    .setBufferSizeInBytes(max(min, hop * bytes * 4))
                    .build()
            } catch (_: Exception) {
                null
            } ?: continue
            if (rec.state == AudioRecord.STATE_INITIALIZED) return Opened(rec, enc == AudioFormat.ENCODING_PCM_FLOAT)
            rec.release()
        }
        return null
    }

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val opened = open()
        if (opened == null) {
            failed = true
            running = false
            return
        }
        val rec = opened.rec
        try {
            onStart(rec.sampleRate)
            val f = FloatArray(hop)
            val s = if (opened.float) null else ShortArray(hop)
            rec.startRecording()
            if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                failed = true
                return
            }
            while (running) {
                var filled = 0
                while (filled < hop && running) {
                    val n = if (s == null) {
                        rec.read(f, filled, hop - filled, AudioRecord.READ_BLOCKING)
                    } else {
                        val r = rec.read(s, filled, hop - filled, AudioRecord.READ_BLOCKING)
                        for (i in filled until filled + max(r, 0)) f[i] = s[i] / 32768f
                        r
                    }
                    if (n < 0) {
                        failed = true
                        running = false
                        break
                    }
                    filled += n
                }
                if (filled == hop) onBlock(f)
            }
        } catch (_: Throwable) {
            failed = true
        } finally {
            runCatching { rec.stop() }
            rec.release()
        }
    }
}

/** Streaming mono AudioTrack, PCM float when possible (16-bit fallback, converted from a reused buffer). */
class PcmOut private constructor(val track: AudioTrack, val isFloat: Boolean, val sampleRate: Int, maxChunk: Int) {
    private val shorts = if (isFloat) null else ShortArray(maxChunk)

    fun write(buf: FloatArray, n: Int): Int {
        if (isFloat) return track.write(buf, 0, n, AudioTrack.WRITE_BLOCKING)
        val s = shorts!!
        for (i in 0 until n) s[i] = (buf[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        return track.write(s, 0, n, AudioTrack.WRITE_BLOCKING)
    }

    fun release() {
        runCatching { track.pause() }
        runCatching { track.flush() }
        runCatching { track.release() }
    }

    companion object {
        fun nativeRate(context: Context): Int {
            val am = context.getSystemService(AudioManager::class.java)
            return am?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()?.takeIf { it in 22050..96000 } ?: 48000
        }

        /** Opens a track; [bufferMs] is the target buffer depth (latency of parameter changes). */
        fun open(context: Context, contentType: Int, lowLatency: Boolean, maxChunk: Int, bufferMs: Int): PcmOut? {
            val rate = nativeRate(context)
            for (enc in intArrayOf(AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_16BIT)) {
                val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, enc)
                if (min <= 0) continue
                val bytes = if (enc == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                val size = max(min, max(maxChunk * 2, rate * bufferMs / 1000) * bytes)
                val track = try {
                    AudioTrack.Builder()
                        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(contentType).build())
                        .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(enc).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setBufferSizeInBytes(size)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .apply { if (lowLatency) setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY) }
                        .build()
                } catch (_: Exception) {
                    null
                } ?: continue
                if (track.state == AudioTrack.STATE_INITIALIZED) return PcmOut(track, enc == AudioFormat.ENCODING_PCM_FLOAT, track.sampleRate, maxChunk)
                track.release()
            }
            return null
        }
    }
}
