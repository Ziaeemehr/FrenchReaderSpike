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
}
