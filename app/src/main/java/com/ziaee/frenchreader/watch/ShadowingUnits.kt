package com.ziaee.frenchreader.watch

data class SentenceUnits(
    val units: List<SubtitleCue>,
    val cueUnitIndices: List<Int>,
    /** Each original cue's cleaned text range inside its unit; dropped cues have no range. */
    val cueTextRanges: List<IntRange?> = emptyList()
) {
    fun unitIndex(cueIndex: Int): Int = cueUnitIndices.getOrNull(cueIndex) ?: -1
}

/**
 * Shadowing and translation use these units; the caption/dictionary timeline stays untouched.
 * [maxMs] bounds joins. An individual longer cue keeps its original timing: splitting it
 * would require word timestamps that imported subtitles do not provide.
 */
fun mergeIntoSentences(cues: List<SubtitleCue>, maxMs: Long = 12_000): List<SubtitleCue> =
    mergeSentenceUnits(cues, maxMs).units

fun mergeSentenceUnits(cues: List<SubtitleCue>, maxMs: Long = 12_000): SentenceUnits {
    require(maxMs > 0)
    val result = mutableListOf<SubtitleCue>()
    var unit: SubtitleCue? = null
    val membership = MutableList(cues.size) { -1 }
    val ranges = MutableList<IntRange?>(cues.size) { null }
    val bracketed = Regex("\\[[^\\]]*]")
    for ((index, cue) in cues.withIndex()) {
        val text = cue.text.replace(bracketed, " ").replace(Regex("\\s+"), " ").trim()
        if (text.isEmpty()) continue
        val previous = unit
        if (previous != null && (isSentenceEnd(previous.text, text) ||
                cue.startMs - previous.endMs > 1200 || maxOf(previous.endMs, cue.endMs) - previous.startMs > maxMs)) {
            result += previous
            unit = null
        }
        membership[index] = result.size
        val start = unit?.let { it.text.length + 1 } ?: 0
        ranges[index] = start until (start + text.length)
        unit = unit?.let { it.copy(endMs = maxOf(it.endMs, cue.endMs), text = it.text + " " + text) }
            ?: cue.copy(text = text)
    }
    unit?.let { result += it }
    // Noise inside a sentence shares its display/navigation unit, without text emphasis.
    cues.forEachIndexed { index, cue ->
        if (membership[index] < 0) {
            membership[index] = result.indexOfFirst { cue.startMs >= it.startMs && cue.endMs <= it.endMs }
        }
    }
    return SentenceUnits(result, membership, ranges)
}

fun shouldAutoPause(timeMs: Long, unit: SubtitleCue?, playingUnit: Boolean, seekObserved: Boolean): Boolean =
    playingUnit && seekObserved && unit != null && timeMs >= unit.endMs + 150
