package com.ziaee.frenchreader.text

/**
 * Splits raw imported text (plain or Markdown) into TTS-request-sized
 * "blocks" -- one edge-tts call, one cache entry, one on-screen unit, one
 * paragraph-translation. [MarkdownParser] then classifies each block
 * (heading / list item / paragraph) and strips its Markdown syntax down to
 * the plain text that actually gets spoken/translated/highlighted.
 *
 * Rules, in order:
 *  - A blank line always ends the current block.
 *  - A heading line ("#" .. "######") is always its own standalone block,
 *    even without a blank line around it (people often paste headings tight
 *    against the next paragraph).
 *  - A list-item line ("-", "*", "+", or "1.") is likewise always its own
 *    standalone block -- one chunk per item -- so each item gets its own
 *    audio/highlight/translation exactly like a short paragraph would, and
 *    the reading screen can simply prefix it with a bullet.
 *  - An EPUB image marker is always its own block.
 *  - Decorative separators, bare URLs, URL-only source labels, and blocks
 *    with no speech text are omitted. This intentionally makes saved chunk
 *    indices from older chunking rules approximate; callers clamp them, and
 *    all current progress/read paths use this same deterministic result.
 *  - Anything else accumulates into the current paragraph until a blank
 *    line, a heading, or a list item interrupts it.
 * A paragraph block that's still too long for one TTS/translation call is
 * split further, on rough sentence boundaries, as a last resort.
 */
object TextChunker {
    private const val MAX_CHUNK_CHARS = 1200
    private val HEADER_LINE = Regex("^#{1,6}\\s+.*$")
    private val LIST_LINE = Regex("^([-*+]|\\d+\\.)\\s+.*$")
    private val SYMBOL_BULLET = Regex("^[•▪◦‣●■►▶✓✔➤→]\\s+(.*)$")
    private val BARE_URL = Regex("(?i)(?:https?://|www\\.)\\S+")
    private val EMPTY_LABEL = Regex("(?i)^(?:source|lien)\\s*:\\s*$")
    private val ASCII_SEPARATOR = Regex("^(?:[-*_~=]\\s*){3,}$")
    private val EM_DASH_SEPARATOR = Regex("^(?:—\\s*){2,}$")
    private val EPUB_IMAGE_LINE = Regex("^!\\[[^]]*]\\(epubimg:[^)]+\\)$")

    fun chunk(rawText: String): List<String> {
        val lines = rawText.replace("\r\n", "\n").split("\n")
        val blocks = mutableListOf<String>()
        val current = mutableListOf<String>()

        fun flush() {
            if (current.isNotEmpty()) {
                blocks.add(current.joinToString(" ").trim())
                current.clear()
            }
        }

        for (rawLine in lines) {
            val line = cleanLine(rawLine)
            when {
                line == null -> flush()
                HEADER_LINE.matches(line) -> {
                    flush()
                    blocks.add(line)
                }
                LIST_LINE.matches(line) -> {
                    flush()
                    blocks.add(line)
                }
                EPUB_IMAGE_LINE.matches(line) -> {
                    flush()
                    blocks.add(line)
                }
                else -> current.add(line)
            }
        }
        flush()

        val out = mutableListOf<String>()
        for (block in blocks) {
            val isSpecial = HEADER_LINE.matches(block) || LIST_LINE.matches(block) || EPUB_IMAGE_LINE.matches(block)
            if (!isSpecial && block.length > MAX_CHUNK_CHARS) {
                out.addAll(splitLongParagraph(block))
            } else {
                out.add(block)
            }
        }
        return out.filter { block ->
            val parsed = MarkdownParser.parse(block)
            parsed.type == BlockType.IMAGE || parsed.spokenText.isNotBlank()
        }
    }

    private fun cleanLine(rawLine: String): String? {
        var line = rawLine.trim()
        if (line.isEmpty() || isSeparatorLine(line)) return null

        SYMBOL_BULLET.matchEntire(line)?.let { match ->
            line = "- ${match.groupValues[1]}"
        }

        line = BARE_URL.replace(line) { match ->
            val start = match.range.first
            if (start >= 2 && line[start - 2] == ']' && line[start - 1] == '(') match.value else ""
        }.replace(Regex("[ \\t]+"), " ").trim()

        return line.takeUnless { it.isEmpty() || EMPTY_LABEL.matches(it) }
    }

    private fun isSeparatorLine(line: String): Boolean {
        if (ASCII_SEPARATOR.matches(line) || EM_DASH_SEPARATOR.matches(line)) return true
        var index = 0
        var foundBoxDrawing = false
        while (index < line.length) {
            val codePoint = Character.codePointAt(line, index)
            index += Character.charCount(codePoint)
            if (Character.isWhitespace(codePoint)) continue
            if (codePoint !in 0x2500..0x257F) return false
            foundBoxDrawing = true
        }
        return foundBoxDrawing
    }

    private fun splitLongParagraph(paragraph: String): List<String> {
        // Rough split on ". " / "! " / "? " followed by a capital letter,
        // just to bound size -- edge-tts still does the real sentence
        // segmentation for highlighting inside each resulting piece.
        val sentenceEnd = Regex("(?<=[.!?…])\\s+(?=[A-ZÀ-Ü«\"])")
        val pieces = paragraph.split(sentenceEnd).flatMap(::splitOversizedUnit)
        val out = mutableListOf<String>()
        val buf = StringBuilder()
        for (p in pieces) {
            if (buf.length + p.length > MAX_CHUNK_CHARS && buf.isNotEmpty()) {
                out.add(buf.toString().trim())
                buf.clear()
            }
            if (buf.isNotEmpty()) buf.append(" ")
            buf.append(p)
        }
        if (buf.isNotEmpty()) out.add(buf.toString().trim())
        return out
    }

    private fun splitOversizedUnit(unit: String): List<String> {
        if (unit.length <= MAX_CHUNK_CHARS) return listOf(unit)
        val result = mutableListOf<String>()
        var remaining = unit.trim()
        while (remaining.length > MAX_CHUNK_CHARS) {
            val whitespace = remaining.lastIndexOf(' ', MAX_CHUNK_CHARS)
            val cut = whitespace.takeIf { it > 0 } ?: MAX_CHUNK_CHARS
            result += remaining.substring(0, cut).trimEnd()
            remaining = remaining.substring(cut).trimStart()
        }
        if (remaining.isNotEmpty()) result += remaining
        return result
    }
}
