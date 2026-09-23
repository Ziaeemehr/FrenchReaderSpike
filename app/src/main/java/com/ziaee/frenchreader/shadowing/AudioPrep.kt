package com.ziaee.frenchreader.shadowing

import kotlinx.coroutines.delay
import kotlin.math.abs

private const val TARGET_PEAK = 16_000 // about half of full scale, leaves headroom
private const val MAX_GAIN = 8

/** The VOICE_RECOGNITION mic source has no AGC: on real phones speech peaks around 5-8% of full
 * scale, so quiet speech reaches the recognizers near the noise floor. Boost it (capped). */
fun normalizeGain(pcm: ShortArray): ShortArray {
    val peak = pcm.maxOfOrNull { abs(it.toInt()) } ?: return pcm
    if (peak == 0 || peak >= TARGET_PEAK) return pcm
    val gain = minOf(TARGET_PEAK.toDouble() / peak, MAX_GAIN.toDouble())
    return ShortArray(pcm.size) { i -> (pcm[i] * gain).toInt().coerceIn(-32_768, 32_767).toShort() }
}

/** Feeds audio in chunks with short pauses: Google's recognizer overflows its buffer
 * (MICROPHONE_AUDIO_BUFFER_OVERFLOW) when a whole recording arrives at once. */
suspend fun writePaced(bytes: ByteArray, chunkBytes: Int, pauseMs: Long, write: (ByteArray) -> Unit) {
    var start = 0
    while (start < bytes.size) {
        val end = minOf(start + chunkBytes, bytes.size)
        write(bytes.copyOfRange(start, end))
        start = end
        if (start < bytes.size) delay(pauseMs)
    }
}
