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
 *  - Anything else accumulates into the current paragraph until a blank
 *    line, a heading, or a list item interrupts it.
 * A paragraph block that's still too long for one TTS/translation call is
 * split further, on rough sentence boundaries, as a last resort.
 */
object TextChunker {
    private const val MAX_CHUNK_CHARS = 1200
    private val HEADER_LINE = Regex("^#{1,6}\\s+.*$")
    private val LIST_LINE = Regex("^([-*+]|\\d+\\.)\\s+.*$")
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
            val line = rawLine.trim()
            when {
                line.isEmpty() -> flush()
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
        return out
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
