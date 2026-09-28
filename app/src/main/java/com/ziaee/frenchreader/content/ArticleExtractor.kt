package com.ziaee.frenchreader.content

import org.jsoup.Jsoup

/** Site-specific full-article extraction for the news and Wikisource
 * sources this app knows about -- see ROADMAP.md section 6. Selectors
 * verified against real, live-fetched pages, not guessed; if a site
 * redesigns, these will need updating (the standard risk of any HTML
 * scraper). */
object ArticleExtractor {
    fun extractFranceInfoArticle(html: String): String? {
        val body = Jsoup.parse(html).selectFirst("div.c-body") ?: return null
        body.select(".media-embed").remove()
        val text = body.select("p, h2, h3").joinToString("\n\n") { it.text() }.trim()
        return text.ifBlank { null }
    }

    fun extractRfiFacileTranscript(html: String): String? {
        val paragraphs = Jsoup.parse(html).select("div.m-transcription__content p")
        val text = paragraphs.joinToString("\n\n") { it.text() }.trim()
        return text.ifBlank { null }
    }

    /** Cleans a Wikisource `action=parse` response body down to the
     * story/chapter's prose -- see ROADMAP.md section 6's Wikisource
     * technical design for why this has to work off rendered HTML rather
     * than Vikidia's plain-text `explaintext`. Strips `.ws-noexport`
     * (edition/footer notices), `.headertemplate` (the author/title block,
     * redundant with this app's own sourceName/author/sourceUrl fields),
     * `.pagenum`/`.ws-pagenum` (inline scan page-number markers that would
     * otherwise get read aloud by TTS as stray numbers) and any leftover
     * `<table>` (edition-list artifacts), then converts real HTML headings
     * to this app's Markdown heading syntax so MarkdownParser.kt's
     * BlockType.HEADER renders and reads them correctly -- same outcome as
     * VikidiaClient's post-launch heading fix, but simpler here since the
     * input is already-rendered HTML, not wikitext `==heading==` syntax. */
    fun extractWikisourceArticle(html: String): String? {
        val doc = Jsoup.parse(html)
        doc.select(".ws-noexport, .headertemplate, .pagenum, .ws-pagenum, table").remove()
        val blocks = doc.select("p, h1, h2, h3, h4, h5, h6").mapNotNull { el ->
            val text = el.text().trim()
            if (text.isBlank()) return@mapNotNull null
            val level = el.tagName().getOrNull(1)?.digitToIntOrNull()
            if (level != null) "#".repeat(level) + " " + text else text
        }
        val text = blocks.joinToString("\n\n").trim()
        return text.ifBlank { null }
    }

    /** Best-effort extraction for news sites without a dedicated selector (the German feeds and
     * the non-RFI French ones). Picks the element whose own paragraphs hold the most prose, so
     * teasers, link lists and footers elsewhere on the page are left out. Pages that ship the
     * article as escaped HTML inside a JSON blob (DW's learner site) are read from that blob.
     * Verified on live Tagesschau, Spiegel, Deutschlandfunk, heise and DW pages (2026-09-28). */
    fun extractGenericArticle(html: String): String? {
        val doc = Jsoup.parse(html)
        doc.select("script, style, noscript, nav, header, footer, aside, figure, form, button").remove()
        // Readability-style scoring: each prose paragraph credits its parent fully and its
        // grandparent by half, so the article body wins over teaser grids and footers.
        val scores = HashMap<org.jsoup.nodes.Element, Int>()
        doc.select(PARAGRAPHS).forEach { paragraph ->
            val length = proseLength(paragraph)
            if (length == 0) return@forEach
            paragraph.parents().take(3).forEachIndexed { depth, ancestor ->
                scores.merge(ancestor, length / (depth + 1), Int::plus)
            }
        }
        val best = scores.maxByOrNull { it.value }
        // Sites that wrap each paragraph separately (Spiegel) spread the body over siblings.
        val fromDom = best?.let { (top, topScore) ->
            val siblings = top.parent()?.children()?.toList() ?: listOf(top)
            siblings.filter {
                it == top || it.tagName() == top.tagName() && it.className() == top.className() &&
                    (scores[it] ?: 0) * 5 >= topScore
            }.flatMap(::proseBlocks)
        }.orEmpty()
        if (fromDom.sumOf { it.length } >= MIN_ARTICLE_CHARS) return fromDom.joinToString("\n\n")
        return extractEmbeddedHtmlArticle(html) ?: fromDom.takeIf { it.isNotEmpty() }?.joinToString("\n\n")
    }

    /** `<p>`, plus leaf `<div>`s some sites (Deutschlandfunk) use as paragraphs. */
    private const val PARAGRAPHS = "p, div:not(:has(div, p, section, article, ul, ol, table, h2, h3))"

    private fun proseBlocks(container: org.jsoup.nodes.Element): List<String> =
        container.select("$PARAGRAPHS, h2, h3").mapNotNull { el ->
            val text = el.text().trim()
            val heading = el.tagName() == "h2" || el.tagName() == "h3"
            when {
                !heading && proseLength(el) > 0 -> text
                heading && text.length in 3..160 -> "## $text"
                else -> null
            }
        }.dropLastWhile { it.startsWith("## ") }

    /** Counts only sentence-like paragraphs, so bylines, dates, "read more" links and teaser links
     * (mostly link text) score zero. */
    private fun proseLength(paragraph: org.jsoup.nodes.Element): Int {
        val text = paragraph.text()
        if (text.length < 60 || text.count { it == ' ' } < 8) return 0
        val linkText = paragraph.select("a").sumOf { it.text().length }
        return if (linkText * 2 > text.length) 0 else text.length
    }

    private fun extractEmbeddedHtmlArticle(html: String): String? {
        val escaped = quotedStrings(html)
            .filter { it.length >= 400 && (it.contains("\\u003cp") || it.contains("<p>")) }
            .maxByOrNull { it.length } ?: return null
        val blocks = proseBlocks(Jsoup.parseBodyFragment(unescapeJsonString(escaped)).body())
        return blocks.takeIf { it.sumOf(String::length) >= MIN_ARTICLE_CHARS }?.joinToString("\n\n")
    }

    /** Every double-quoted, backslash-escaped string literal in [text], scanned linearly (a regex
     * over half-megabyte pages overflows the stack). */
    private fun quotedStrings(text: String): Sequence<String> = sequence {
        var i = text.indexOf('"')
        while (i >= 0 && i < text.length) {
            var j = i + 1
            while (j < text.length && text[j] != '"') j += if (text[j] == '\\') 2 else 1
            if (j >= text.length) break
            yield(text.substring(i + 1, j))
            i = text.indexOf('"', j + 1)
        }
    }

    internal fun unescapeJsonString(value: String): String {
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c != '\\' || i + 1 >= value.length) { out.append(c); i++; continue }
            when (val next = value[i + 1]) {
                'n' -> out.append('\n')
                't' -> out.append('\t')
                'r' -> {}
                'u' -> {
                    val code = value.substring(i + 2, minOf(i + 6, value.length)).toIntOrNull(16)
                    if (code != null) { out.append(code.toChar()); i += 4 } else out.append("\\u")
                }
                else -> out.append(next)
            }
            i += 2
        }
        return out.toString()
    }

    private const val MIN_ARTICLE_CHARS = 400
}
