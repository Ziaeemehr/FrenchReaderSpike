package com.ziaee.frenchreader.grammar

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight

/** Code spans longer than this are shown on their own LTR line instead of inline. */
const val LONG_CODE_SPAN = 24

/** A piece of a Persian paragraph: running prose, or a long French code span lifted out of it. */
data class ProseSegment(val text: String, val longCode: Boolean)

fun splitLongCode(text: String): List<ProseSegment> {
    val segments = mutableListOf<ProseSegment>()
    val prose = StringBuilder()
    fun flush() {
        val pending = prose.toString().trim()
        // Punctuation left dangling after a lifted span would sit alone on a line.
        if (pending.any { it.isLetterOrDigit() }) segments.add(ProseSegment(pending, false))
        prose.clear()
    }
    var i = 0
    while (i < text.length) {
        val end = if (text[i] == '`') text.indexOf('`', i + 1) else -1
        if (end > i && end - i - 1 > LONG_CODE_SPAN) {
            flush()
            segments.add(ProseSegment(text.substring(i + 1, end), true))
            i = end + 1
        } else if (end > i) {
            prose.append(text, i, end + 1)
            i = end + 1
        } else {
            prose.append(text[i++])
        }
    }
    flush()
    return segments
}

/** Offsets map original UTF-16 positions into text containing directional isolates. */
data class IsolatedText(val text: String, val offsets: List<Int>, val code: List<HighlightRange>)

fun isolateGrammarText(source: String, rtl: Boolean, markdown: Boolean = false): IsolatedText {
    val output = StringBuilder()
    val offsets = MutableList(source.length + 1) { 0 }
    val codes = mutableListOf<HighlightRange>()
    var i = 0
    fun direction(c: Char): Int = when (Character.getDirectionality(c)) {
        Character.DIRECTIONALITY_LEFT_TO_RIGHT -> 1
        Character.DIRECTIONALITY_RIGHT_TO_LEFT, Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> -1
        else -> 0
    }
    val opposite = if (rtl) 1 else -1
    val host = -opposite
    val pairs = mapOf(')' to '(', ']' to '[', '»' to '«', '”' to '“')
    fun startsRun(index: Int): Boolean {
        if (direction(source[index]) == opposite) return true
        if (source[index] !in pairs.values) return false
        var next = index + 1
        while (next < source.length && source[next].isWhitespace()) next++
        return next < source.length && direction(source[next]) == opposite
    }
    while (i < source.length) {
        val codeEnd = if (markdown && source[i] == '`') source.indexOf('`', i + 1) else -1
        if (codeEnd > i) {
            offsets[i] = output.length
            output.append('\u2066')
            val start = output.length
            // Short spans wrap as one unit; a split LTR run inside an RTL line reads out of order.
            val raw = source.substring(i + 1, codeEnd)
            val content = isolateGrammarText(if (raw.length <= LONG_CODE_SPAN) raw.replace(' ', '\u00A0') else raw, rtl = false)
            for (j in i + 1 until codeEnd) offsets[j] = start + content.offsets[j - i - 1]
            output.append(content.text)
            codes.add(HighlightRange(start, output.length))
            offsets[codeEnd] = output.length
            output.append('\u2069')
            i = codeEnd + 1
        } else if (startsRun(i)) {
            val start = i
            var end = i + 1
            while (end < source.length && source[end] != '`' && source[end] !in "،؛؟" && direction(source[end]) != host) end++
            while (end > start + 1) {
                val last = source[end - 1]
                val opener = pairs[last]
                val paired = opener != null && source.substring(start, end - 1).count { it == opener } >
                    source.substring(start, end - 1).count { it == last }
                if (direction(last) == opposite || last.isDigit() || last == '\'' || paired) break
                end--
            }
            output.append(if (rtl) '\u2066' else '\u2067')
            while (i < end) {
                offsets[i] = output.length
                output.append(source[i++])
            }
            output.append('\u2069')
        } else {
            offsets[i] = output.length
            // An arrow between a Persian phrase and what follows must point the way the line reads.
            output.append(if (rtl && source[i] == '→') '←' else source[i])
            i++
        }
    }
    offsets[source.length] = output.length
    return IsolatedText(output.toString(), offsets, codes)
}

fun grammarAnnotatedText(
    cell: GrammarCell, rtl: Boolean, target: Color, codeBackground: Color, markdown: Boolean = false
): AnnotatedString {
    val isolated = isolateGrammarText(cell.text, rtl, markdown)
    return AnnotatedString.Builder(isolated.text).apply {
        cell.highlights.forEach {
            addStyle(SpanStyle(color = target, fontWeight = FontWeight.Bold), isolated.offsets[it.start], isolated.offsets[it.end])
        }
        isolated.code.forEach {
            addStyle(SpanStyle(color = target, background = codeBackground), it.start, it.end)
        }
    }.toAnnotatedString()
}
