package com.ziaee.frenchreader.shadowing

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlin.concurrent.thread

const val SAMPLE_RATE = 16_000
private const val MAX_SAMPLES = SAMPLE_RATE * 30

internal fun durationMsOf(samples: Int): Long = samples * 1000L / SAMPLE_RATE

/** Records mono 16 kHz PCM into memory. Never touches disk. Caller must hold RECORD_AUDIO. */
class MicRecorder {
    private var record: AudioRecord? = null
    private var worker: Thread? = null
    @Volatile private var running = false
    private val buffer = ShortArray(MAX_SAMPLES)
    @Volatile private var size = 0

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val r = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, 4096))
        record = r
        size = 0
        running = true
        r.startRecording()
        worker = thread(name = "mic-recorder") {
            val chunk = ShortArray(1024)
            while (running && size < MAX_SAMPLES) {
                val n = r.read(chunk, 0, minOf(chunk.size, MAX_SAMPLES - size))
                if (n > 0) { System.arraycopy(chunk, 0, buffer, size, n); size += n }
            }
        }
    }

    fun stop(): ShortArray {
        running = false
        worker?.join(500)
        worker = null
        record?.run { runCatching { stop() }; release() }
        record = null
        return buffer.copyOf(size)
    }

    fun cancel() { stop(); size = 0 }
}

/** Plays back an in-memory recording for "Replay mine". */
object PcmPlayer {
    private var track: AudioTrack? = null

    fun play(pcm: ShortArray) {
        stop()
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(maxOf(pcm.size * 2, 2))
            .build()
        t.write(pcm, 0, pcm.size)
        t.play()
        track = t
    }

    fun stop() { track?.run { runCatching { stop() }; release() }; track = null }
}
