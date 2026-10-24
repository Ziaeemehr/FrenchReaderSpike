package com.ziaee.frenchreader.watch

import org.json.JSONObject
import org.jsoup.parser.Parser

data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)

data class SubtitleToken(val text: String, val isWord: Boolean)

fun tokenizeSubtitle(text: String): List<SubtitleToken> {
    val pattern = Regex("(?:[lLdDjJtTmMsScCnN]|[qQ]u)[’ʼ']|[\\p{L}\\p{M}]+(?:[’ʼ'-][\\p{L}\\p{M}]+)*|[^\\p{L}\\p{M}]+")
    return pattern.findAll(text).map { SubtitleToken(it.value, it.value.any(Char::isLetter)) }.toList()
}

fun findCueIndex(cues: List<SubtitleCue>, timeMs: Long): Int {
    var low = 0
    var high = cues.lastIndex
    var found = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (cues[mid].startMs <= timeMs) {
            found = mid
            low = mid + 1
        } else high = mid - 1
    }
    return found.takeIf { it >= 0 && timeMs < cues[it].endMs } ?: -1
}

internal fun cleanSubtitleText(text: String): String = Parser.unescapeEntities(
    text.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]*>"), ""), false
).replace('\u00a0', ' ').lines().joinToString("\n") { it.trim() }.trim()

internal fun normalizeCues(cues: List<SubtitleCue>): List<SubtitleCue> {
    val sorted = cues.filter { it.startMs >= 0 && it.endMs > it.startMs && it.text.isNotBlank() }
        .sortedBy { it.startMs }
        .groupBy { it.startMs }.map { (_, sameStart) ->
            // Two speakers shown at once in an imported file: keep both lines in one cue.
            sameStart.reduce { a, b -> a.copy(endMs = maxOf(a.endMs, b.endMs), text = a.text + "\n" + b.text) }
        }
    return sorted.mapIndexedNotNull { index, cue ->
        val end = minOf(cue.endMs, sorted.getOrNull(index + 1)?.startMs ?: Long.MAX_VALUE)
        cue.copy(endMs = end).takeIf { end > cue.startMs }
    }
}

private fun timestamp(raw: String): Long? {
    val parts = raw.replace(',', '.').split(':')
    if (parts.size !in 2..3) return null
    val secondsParts = parts.last().split('.')
    if (secondsParts.size != 2 || secondsParts[1].length != 3) return null
    val seconds = secondsParts[0].toLongOrNull()?.takeIf { it in 0..59 } ?: return null
    val millis = secondsParts[1].toLongOrNull() ?: return null
    val minutes = parts[parts.lastIndex - 1].toLongOrNull()?.takeIf { it in 0..59 } ?: return null
    val hours = if (parts.size == 3) parts[0].toLongOrNull()?.takeIf { it in 0..100_000 } ?: return null else 0
    return ((hours * 60 + minutes) * 60 + seconds) * 1000 + millis
}

private fun parseTimedBlocks(input: String, vtt: Boolean): List<SubtitleCue> {
    val blocks = input.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        .split(Regex("\n[ \\t]*\n"))
    val timing = Regex("^\\s*(\\S+)\\s+-->\\s+(\\S+).*$")
    val cues = blocks.mapNotNull { block ->
        val lines = block.trim().lines()
        if (vtt && lines.firstOrNull()?.let { it == "WEBVTT" || it.startsWith("WEBVTT ") ||
                    it == "STYLE" || it == "REGION" || it == "NOTE" || it.startsWith("NOTE ") } == true) return@mapNotNull null
        val index = lines.indexOfFirst { timing.matches(it) }
        if (index !in 0..1) return@mapNotNull null
        val match = timing.matchEntire(lines[index]) ?: return@mapNotNull null
        val start = timestamp(match.groupValues[1]) ?: return@mapNotNull null
        val end = timestamp(match.groupValues[2]) ?: return@mapNotNull null
        SubtitleCue(start, end, cleanSubtitleText(lines.drop(index + 1).joinToString("\n")))
    }
    return normalizeCues(cues)
}

object SrtParser {
    fun parse(input: String): List<SubtitleCue> = parseTimedBlocks(input, false)
}

object VttParser {
    fun parse(input: String): List<SubtitleCue> = parseTimedBlocks(input, true)
}

object YoutubeTimedTextParser {
    fun parse(input: String): List<SubtitleCue> {
        val events = JSONObject(input).optJSONArray("events") ?: return emptyList()
        val cues = mutableListOf<SubtitleCue>()
        for (index in 0 until events.length()) {
            val event = events.optJSONObject(index) ?: continue
            val segments = event.optJSONArray("segs") ?: continue
            val start = event.optLong("tStartMs", -1)
            val duration = event.optLong("dDurationMs", 0)
            if (start < 0 || duration <= 0 || start > Long.MAX_VALUE - duration) continue
            // Empty/whitespace segments do not supply the next token (notably after etc.).
            val lookahead = MutableList(segments.length() + 1) { "" }
            for (i in segments.length() - 1 downTo 0) {
                lookahead[i] = cleanSubtitleText(segments.optJSONObject(i)?.optString("utf8").orEmpty())
                    .ifBlank { lookahead[i + 1] }
            }
            var cueStart = start
            val text = StringBuilder()
            for (segmentIndex in 0 until segments.length()) {
                val segment = segments.optJSONObject(segmentIndex)
                text.append(segment?.optString("utf8").orEmpty())
                val next = segments.optJSONObject(segmentIndex + 1)
                // Only split where the next word supplies a valid event-relative boundary.
                val offset = next?.optLong("tOffsetMs", -1) ?: -1
                if (isSentenceEnd(cleanSubtitleText(text.toString()),
                        lookahead[segmentIndex + 1]) &&
                    offset in 1 until duration && start + offset > cueStart) {
                    cues += SubtitleCue(cueStart, start + offset, cleanSubtitleText(text.toString()))
                    cueStart = start + offset
                    text.clear()
                }
            }
            cues += SubtitleCue(cueStart, start + duration, cleanSubtitleText(text.toString()))
        }
        return normalizeCues(cues)
    }
}

fun parseSubtitleFile(name: String?, content: String): List<SubtitleCue> =
    if (content.trimStart('\uFEFF', ' ', '\n', '\r', '\t').startsWith("WEBVTT") || name?.endsWith(".vtt", true) == true)
        VttParser.parse(content) else SrtParser.parse(content)
