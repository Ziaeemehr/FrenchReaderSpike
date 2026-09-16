package com.ziaee.frenchreader.util

/** Strips HTML tags and unescapes the handful of entities RSS/wiki feeds
 * actually use -- not a full HTML parser, just enough for a feed
 * description or search snippet (a paragraph or two, occasionally a stray
 * `<p>`/`<br>` or an `&nbsp;`), collapsing whitespace left behind by
 * removed tags. */
object HtmlUtil {
    fun stripHtml(html: String): String {
        val noTags = html.replace(Regex("<[^>]*>"), " ")
        return noTags
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("\\s+"), " ")
            .replace(Regex(" +([.,])"), "$1")
            .trim()
    }
}
