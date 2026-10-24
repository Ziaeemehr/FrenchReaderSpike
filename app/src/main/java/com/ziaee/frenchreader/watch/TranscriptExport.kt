package com.ziaee.frenchreader.watch

import java.util.Locale

/** Reading paragraphs share the sentence boundaries used by shadowing. */
fun transcriptParagraphs(cues: List<SubtitleCue>): String {
    val paragraphs = mutableListOf<String>()
    val sentences = mutableListOf<String>()
    var previousEnd: Long? = null
    for (unit in mergeSentenceUnits(cues).units) {
        val text = unit.text.replace(Regex("\\[[^\\]]*]"), " ").replace(Regex("\\s+"), " ").trim()
        if (text.isEmpty()) continue
        if (sentences.isNotEmpty() && (sentences.size >= 6 ||
                previousEnd?.let { unit.startMs - it >= 2000 } == true)) {
            paragraphs += sentences.joinToString(" ")
            sentences.clear()
        }
        sentences += text
        previousEnd = unit.endMs
    }
    if (sentences.isNotEmpty()) paragraphs += sentences.joinToString(" ")
    return paragraphs.joinToString("\n\n")
}

/** Export original cue timing and line breaks, independently of reading paragraphs. */
fun transcriptSrt(cues: List<SubtitleCue>): String = buildString {
    cues.forEachIndexed { index, cue ->
        append(index + 1).append('\n')
        append(srtTimestamp(cue.startMs)).append(" --> ").append(srtTimestamp(cue.endMs)).append('\n')
        append(cue.text.replace("\r\n", "\n").replace('\r', '\n')).append("\n\n")
    }
}

private fun srtTimestamp(timeMs: Long): String {
    val ms = timeMs.coerceAtLeast(0)
    return String.format(Locale.ROOT, "%02d:%02d:%02d,%03d",
        ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, ms % 1000)
}

fun transcriptFileName(title: String): String =
    title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trim('.')
        .take(100).ifBlank { "YouTube" } + ".srt"

/** All library writes use the same paragraph format as export. */
internal fun transcriptOf(cues: List<SubtitleCue>): String = transcriptParagraphs(cues)
