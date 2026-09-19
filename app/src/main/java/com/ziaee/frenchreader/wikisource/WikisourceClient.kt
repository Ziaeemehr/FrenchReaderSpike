package com.ziaee.frenchreader.wikisource

import com.ziaee.frenchreader.util.HtmlUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One Wikisource search hit -- see ROADMAP.md section 6's Wikisource
 * technical design. Unlike Vikidia, [title] alone is enough to fetch the
 * full article ([WikisourceClient.fetchArticle] takes `action=parse&page=`,
 * not a numeric page id), so there's no separate id field to carry around. */
data class WikisourceSearchResult(
    val title: String,
    val snippet: String,
    val wordCount: Int
)

/** A fetched Wikisource page: [html] is the raw rendered article body,
 * still needing [com.ziaee.frenchreader.content.ArticleExtractor.extractWikisourceArticle]
 * to strip scan/edition chrome -- kept out of this client (unlike
 * VikidiaClient's self-contained heading fix) so this package doesn't
 * depend on the `content` package that already depends on it, the same
 * way NewsContentSource fetches raw HTML and hands it to ArticleExtractor
 * rather than cleaning it itself. [title] is the API's canonical title,
 * which can differ from the title that was searched for (e.g. a redirect
 * like "Boule de Suif" resolving to "Boule de suif (recueil)/Boule de
 * Suif") -- using it for both the saved title and [WikisourceClient.articleUrl]
 * keeps the "open in browser" link correct. */
data class WikisourcePage(
    val title: String,
    val html: String
)

/**
 * Client for French Wikisource's public MediaWiki API (fr.wikisource.org)
 * -- see ROADMAP.md section 6. Dependency-free like VikidiaClient:
 * HttpURLConnection + org.json, no OkHttp/Retrofit/MediaWiki client
 * library. Uses `action=parse` rather than Vikidia's `prop=extracts`,
 * because Wikisource's extracts API is disabled (verified with a live
 * call, not assumed) and many proofread works don't store any prose in
 * their wikitext at all -- `action=parse` is the one endpoint that
 * resolves both a plain article's wikitext and a proofread work's
 * `<pages index=... />` transclusion into the same full rendered HTML.
 */
object WikisourceClient {
    private const val API_BASE = "https://fr.wikisource.org/w/api.php"

    suspend fun search(query: String, limit: Int = 10): List<WikisourceSearchResult> = withContext(Dispatchers.IO) {
        val url = "$API_BASE?action=query&list=search&format=json&srlimit=$limit" +
            "&srprop=snippet%7Cwordcount&srsearch=" + URLEncoder.encode(query, "UTF-8")
        parseSearchResults(httpGet(url))
    }

    suspend fun fetchPage(title: String): WikisourcePage? = withContext(Dispatchers.IO) {
        val url = "$API_BASE?action=parse&format=json&prop=text&page=" + URLEncoder.encode(title, "UTF-8")
        parsePage(httpGet(url))
    }

    /** Wikisource's canonical article URL, used for [com.ziaee.frenchreader.data.TextDocument.sourceUrl]
     * and the "open in browser" action -- same convention as [com.ziaee.frenchreader.vikidia.VikidiaClient.articleUrl]. */
    fun articleUrl(title: String): String =
        "https://fr.wikisource.org/wiki/" + URLEncoder.encode(title.replace(' ', '_'), "UTF-8")

    internal fun parseSearchResults(json: String): List<WikisourceSearchResult> {
        val search = JSONObject(json).optJSONObject("query")?.optJSONArray("search") ?: return emptyList()
        return (0 until search.length()).map { i ->
            val obj = search.getJSONObject(i)
            WikisourceSearchResult(
                title = obj.getString("title"),
                snippet = HtmlUtil.stripHtml(obj.optString("snippet", "")),
                wordCount = obj.optInt("wordcount", 0)
            )
        }
    }

    internal fun parsePage(json: String): WikisourcePage? {
        val parse = JSONObject(json).optJSONObject("parse") ?: return null
        val title = parse.optString("title", "")
        val html = parse.optJSONObject("text")?.optString("*", "") ?: ""
        if (title.isBlank() || html.isBlank()) return null
        return WikisourcePage(title = title, html = html)
    }

    private fun httpGet(urlString: String): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; FrenchReaderApp)")
        connection.setRequestProperty("Accept", "application/json")
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
