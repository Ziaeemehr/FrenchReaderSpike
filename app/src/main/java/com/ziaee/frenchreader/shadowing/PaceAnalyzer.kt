package com.ziaee.frenchreader.shadowing

import kotlin.math.sqrt

enum class PaceRating { FAST, GOOD, SLOW }
data class PaceResult(val ratio: Float, val rating: PaceRating)

/** Rhythm proxy: how long the learner actually spoke vs. how long the TTS sentence lasts. */
object PaceAnalyzer {
    private const val FRAME_MS = 20
    private const val MIN_SPEECH_MS = 300L
    private const val ABS_THRESHOLD = 300.0
    private const val REL_THRESHOLD = 0.15

    fun speechDurationMs(pcm: ShortArray, sampleRate: Int): Long {
        val frame = sampleRate * FRAME_MS / 1000
        if (frame <= 0 || pcm.size < frame) return 0
        val rms = DoubleArray(pcm.size / frame) { f ->
            var sum = 0.0
            for (i in f * frame until (f + 1) * frame) {
                val v = pcm[i].toDouble()
                sum += v * v
            }
            sqrt(sum / frame)
        }
        val threshold = maxOf(ABS_THRESHOLD, (rms.maxOrNull() ?: 0.0) * REL_THRESHOLD)
        val first = rms.indexOfFirst { it > threshold }
        if (first < 0) return 0
        val last = rms.indexOfLast { it > threshold }
        return (last - first + 1).toLong() * FRAME_MS
    }

    fun rate(ratio: Float): PaceRating = when {
        ratio < 0.8f -> PaceRating.FAST
        ratio > 1.3f -> PaceRating.SLOW
        else -> PaceRating.GOOD
    }

    fun pace(pcm: ShortArray?, sampleRate: Int, expectedMs: Long): PaceResult? {
        if (pcm == null || expectedMs <= 0) return null
        val speech = speechDurationMs(pcm, sampleRate)
        if (speech < MIN_SPEECH_MS) return null
        val ratio = speech.toFloat() / expectedMs
        return PaceResult(ratio, rate(ratio))
    }
}
