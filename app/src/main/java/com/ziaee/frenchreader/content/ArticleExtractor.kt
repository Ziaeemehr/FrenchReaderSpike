package com.ziaee.frenchreader.content

import org.jsoup.Jsoup

/** Site-specific full-article extraction for the two news sources this app
 * knows about -- see ROADMAP.md section 6. Selectors verified against real,
 * live-fetched pages, not guessed; if either site redesigns, these will
 * need updating (the standard risk of any HTML scraper). */
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
}
