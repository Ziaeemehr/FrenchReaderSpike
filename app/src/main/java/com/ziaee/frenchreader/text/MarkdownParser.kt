package com.ziaee.frenchreader.text

/**
 * How a TextChunker "block" should be displayed. TextChunker already
 * guarantees a HEADER or LIST_ITEM block is exactly one source line, and a
 * PARAGRAPH block is one or more wrapped lines -- see TextChunker's kdoc.
 */
enum class BlockType { HEADER, LIST_ITEM, PARAGRAPH }

/**
 * A bold/italic run, measured as a [start, end) character range against the
 * SAME plain (Markdown-syntax-stripped) string that is sent to edge-tts and
 * to the translation service -- so it lines up with the spoken text, not
 * with the original Markdown source.
 */
data class EmphasisSpan(val start: Int, val end: Int, val bold: Boolean, val italic: Boolean)

data class ParsedBlock(
    val type: BlockType,
    val headerLevel: Int = 0,
    val listOrdered: Boolean = false,
    val plainText: String,
    val emphasisSpans: List<EmphasisSpan> = emptyList()
)

/**
 * Turns one raw TextChunker block (which may still carry Markdown syntax)
 * into a [ParsedBlock]: a block type for rendering (heading size, list
 * bullet, plain paragraph) plus a Markdown-stripped [ParsedBlock.plainText]
 * -- the exact string that gets sent to TTS/translation and that
 * ReadingViewModel/ReadingScreen highlight sentence-by-sentence. Bold/italic
 * markers are stripped too, with their positions recorded as
 * [EmphasisSpan]s so the UI can re-apply them on top of the highlighted
 * text.
 *
 * This is a small, pragmatic Markdown subset -- headings, unordered/ordered
 * list items, bold ("**x**" or "__x__"), italic ("*x*" or "_x_"), and
 * [text](url) links (kept as their visible text; the URL is dropped, never
 * auto-fetched).
 * Images (`![alt](url)`) are dropped entirely, matching the design doc's
 * "no auto-loaded images". Anything else (tables, code fences, footnotes)
 * just falls through to plain paragraph text.
 */
object MarkdownParser {
    private val HEADER = Regex("^(#{1,6})\\s+(.*)$")
    private val LIST_ITEM = Regex("^([-*+]|\\d+\\.)\\s+(.*)$")

    fun parse(rawBlock: String): ParsedBlock {
        val trimmed = rawBlock.trim()

        HEADER.find(trimmed)?.let { m ->
            val level = m.groupValues[1].length
            val (text, spans) = stripInlineEmphasis(m.groupValues[2].trim())
            return ParsedBlock(BlockType.HEADER, headerLevel = level, plainText = text, emphasisSpans = spans)
        }

        LIST_ITEM.find(trimmed)?.let { m ->
            val ordered = m.groupValues[1].firstOrNull()?.isDigit() == true
            val (text, spans) = stripInlineEmphasis(m.groupValues[2].trim())
            return ParsedBlock(BlockType.LIST_ITEM, listOrdered = ordered, plainText = text, emphasisSpans = spans)
        }

        // Plain paragraph: join any wrapped lines into one flowing string.
        val joined = trimmed.lines().joinToString(" ") { it.trim() }
            .replace(Regex("\\s+"), " ")
            .trim()
        val (text, spans) = stripInlineEmphasis(joined)
        return ParsedBlock(BlockType.PARAGRAPH, plainText = text, emphasisSpans = spans)
    }

    /**
     * Walks [raw] once, stripping `**bold**` / `__bold__` / `*italic*` /
     * `_italic_` / `[text](url)` / `![alt](url)` syntax and returning the
     * plain text plus emphasis spans measured against THAT plain text.
     * Unmatched delimiters (e.g. a lone "*" in normal prose) are left as
     * literal characters rather than risk mangling ordinary text.
     */
    private fun stripInlineEmphasis(raw: String): Pair<String, List<EmphasisSpan>> {
        val out = StringBuilder()
        val spans = mutableListOf<EmphasisSpan>()
        var i = 0
        val n = raw.length

        while (i < n) {
            val c = raw[i]

            // Image: ![alt](url) -- dropped entirely, no placeholder text.
            if (c == '!' && i + 1 < n && raw[i + 1] == '[') {
                val closeBracket = raw.indexOf(']', i + 2)
                val closeParen = if (closeBracket >= 0 && closeBracket + 1 < n && raw[closeBracket + 1] == '(')
                    raw.indexOf(')', closeBracket + 2) else -1
                if (closeBracket >= 0 && closeParen >= 0) {
                    i = closeParen + 1
                    continue
                }
            }

            // Link: [text](url) -> text (emphasis inside the link text still applies).
            if (c == '[') {
                val closeBracket = raw.indexOf(']', i + 1)
                val closeParen = if (closeBracket >= 0 && closeBracket + 1 < n && raw[closeBracket + 1] == '(')
                    raw.indexOf(')', closeBracket + 2) else -1
                if (closeBracket >= 0 && closeParen >= 0) {
                    val (innerText, innerSpans) = stripInlineEmphasis(raw.substring(i + 1, closeBracket))
                    val start = out.length
                    out.append(innerText)
                    innerSpans.forEach { spans.add(it.copy(start = it.start + start, end = it.end + start)) }
                    i = closeParen + 1
                    continue
                }
            }

            // Bold: **text** or __text__
            val boldDelim = listOf("**", "__").firstOrNull { d -> raw.regionMatches(i, d, 0, d.length) }
            if (boldDelim != null) {
                val close = raw.indexOf(boldDelim, i + boldDelim.length)
                if (close > i) {
                    val start = out.length
                    out.append(raw.substring(i + boldDelim.length, close))
                    if (out.length > start) spans.add(EmphasisSpan(start, out.length, bold = true, italic = false))
                    i = close + boldDelim.length
                    continue
                }
            }

            // Italic: *text* or _text_
            if (c == '*' || c == '_') {
                val delim = c.toString()
                val close = raw.indexOf(delim, i + 1)
                if (close > i) {
                    val start = out.length
                    out.append(raw.substring(i + 1, close))
                    if (out.length > start) spans.add(EmphasisSpan(start, out.length, bold = false, italic = true))
                    i = close + 1
                    continue
                }
            }

            out.append(c)
            i++
        }
        return out.toString() to spans
    }
}
