package com.ziaee.frenchreader.vikidia

import com.ziaee.frenchreader.util.HtmlUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One Vikidia search hit: enough to show a result row (title + snippet +
 * an approximate length) without a second request per result -- the search
 * API returns wordcount alongside the snippet in a single call. */
data class VikidiaSearchResult(
    val pageId: Int,
    val title: String,
    val snippet: String,
    val wordCount: Int
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
