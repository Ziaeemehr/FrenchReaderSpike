package com.ziaee.frenchreader.vikidia

import com.ziaee.frenchreader.util.HtmlUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** One Vikidia search hit: enough to show a result row (title + snippet +
 * an approximate length) without a second request per result -- the search
 * API returns wordcount alongside the snippet in a single call. */
data class VikidiaSearchResult(
    val pageId: Int,
    val title: String,
    val snippet: String,
    val wordCount: Int
)

/** A fetched Vikidia article: plain-text body (already stripped of
 * wiki markup by the API's explaintext mode) plus the source's last
 * revision date, used as [com.ziaee.frenchreader.data.TextDocument.publishedAt]. */
data class VikidiaArticle(
    val title: String,
    val text: String,
    val publishedAtMs: Long?
)

/**
 * Client for Vikidia's public MediaWiki API (fr.vikidia.org) -- see
 * ROADMAP.md section 6. Dependency-free like NewsFetcher: HttpURLConnection
 * + org.json, no OkHttp/Retrofit/MediaWiki client library.
 */
object VikidiaClient {
    private const val API_BASE = "https://fr.vikidia.org/w/api.php"

    suspend fun search(query: String, limit: Int = 10): List<VikidiaSearchResult> = withContext(Dispatchers.IO) {
        val url = "$API_BASE?action=query&list=search&format=json&srlimit=$limit" +
            "&srprop=snippet%7Cwordcount&srsearch=" + URLEncoder.encode(query, "UTF-8")
        parseSearchResults(httpGet(url))
    }

    suspend fun fetchArticle(pageId: Int): VikidiaArticle? = withContext(Dispatchers.IO) {
        val url = "$API_BASE?action=query&prop=extracts%7Crevisions&explaintext=1" +
            "&rvprop=timestamp&format=json&pageids=$pageId"
        parseArticle(httpGet(url), pageId)
    }

    /** Vikidia's canonical article URL, used for [com.ziaee.frenchreader.data.TextDocument.sourceUrl]
     * and the "open in browser" action -- spaces become underscores per
     * MediaWiki's URL convention, and the result is URL-encoded. */
    fun articleUrl(title: String): String =
        "https://fr.vikidia.org/wiki/" + URLEncoder.encode(title.replace(' ', '_'), "UTF-8")

    internal fun parseSearchResults(json: String): List<VikidiaSearchResult> {
        val search = JSONObject(json).optJSONObject("query")?.optJSONArray("search") ?: return emptyList()
        return (0 until search.length()).map { i ->
            val obj = search.getJSONObject(i)
            VikidiaSearchResult(
                pageId = obj.getInt("pageid"),
                title = obj.getString("title"),
                snippet = HtmlUtil.stripHtml(obj.optString("snippet", "")),
                wordCount = obj.optInt("wordcount", 0)
            )
        }
    }

    internal fun parseArticle(json: String, pageId: Int): VikidiaArticle? {
        val page = JSONObject(json).optJSONObject("query")?.optJSONObject("pages")
            ?.optJSONObject(pageId.toString()) ?: return null
        val title = page.optString("title", "")
        val extract = page.optString("extract", "").trim()
        if (title.isBlank() || extract.isBlank()) return null
        val timestamp = page.optJSONArray("revisions")?.optJSONObject(0)?.optString("timestamp")
        return VikidiaArticle(title = title, text = extract, publishedAtMs = timestamp?.let(::parseIso8601))
    }

    private fun parseIso8601(timestamp: String): Long? = try {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .parse(timestamp)?.time
    } catch (e: Exception) {
        null
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
