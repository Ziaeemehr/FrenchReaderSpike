package com.ziaee.frenchreader.watch

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class VideoCaptions(val title: String, val cues: List<SubtitleCue>, val channel: String = "")

interface CaptionSource {
    suspend fun fetch(videoId: String): VideoCaptions
}

data class CaptionTrack(val baseUrl: String, val languageCode: String, val kind: String, val translateToFrench: Boolean = false)

fun selectCaptionTrack(response: JSONObject): CaptionTrack? {
    val tracks = response.optJSONObject("captions")?.optJSONObject("playerCaptionsTracklistRenderer")
        ?.optJSONArray("captionTracks") ?: return null
    val all = (0 until tracks.length()).mapNotNull { index ->
        val track = tracks.optJSONObject(index) ?: return@mapNotNull null
        val url = track.optString("baseUrl").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        CaptionTrack(url, track.optString("languageCode"), track.optString("kind"))
    }
    fun CaptionTrack.french() = languageCode.startsWith("fr", true)
    return all.firstOrNull { it.french() && it.kind != "asr" }
        ?: all.firstOrNull { it.french() && it.kind == "asr" }
        ?: all.firstOrNull()?.copy(translateToFrench = true)
}

fun playerVideoTitle(response: JSONObject): String = response.optJSONObject("videoDetails")?.optString("title").orEmpty()

fun extractPlayerResponse(html: String): JSONObject {
    val marker = Regex("(?:var\\s+)?ytInitialPlayerResponse\\s*=\\s*|[\"']ytInitialPlayerResponse[\"']\\s*:\\s*")
    for (match in marker.findAll(html)) {
        val start = html.indexOf('{', match.range.last + 1)
        if (start < 0) continue
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until html.length) {
            val ch = html[index]
            if (inString) {
                if (escaped) escaped = false
                else if (ch == '\\') escaped = true
                else if (ch == '"') inString = false
            } else when (ch) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> if (--depth == 0) return JSONObject(html.substring(start, index + 1))
            }
        }
    }
    error("Missing player response")
}

/** Innertube client asked for the player response. Since 2025 YouTube refuses caption downloads made with
 * the web client's track URLs unless they carry a proof-of-origin token; the mobile clients' URLs still work. */
internal data class InnertubeClient(val name: String, val version: String, val userAgent: String, val extra: Map<String, Any>)

internal val innertubeClients = listOf(
    InnertubeClient("ANDROID", "20.10.38", "com.google.android.youtube/20.10.38 (Linux; U; Android 11) gzip",
        mapOf("androidSdkVersion" to 30, "osName" to "Android", "osVersion" to "11")),
    InnertubeClient("IOS", "20.10.4", "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
        mapOf("deviceMake" to "Apple", "deviceModel" to "iPhone16,2", "osName" to "iPhone", "osVersion" to "18.3.2.22D82"))
)

internal fun innertubePlayerRequest(client: InnertubeClient, videoId: String): String {
    val context = JSONObject().put("clientName", client.name).put("clientVersion", client.version)
        .put("hl", "fr").put("gl", "FR")
    client.extra.forEach { (key, value) -> context.put(key, value) }
    return JSONObject().put("context", JSONObject().put("client", context)).put("videoId", videoId).toString()
}

internal fun timedTextUrl(track: CaptionTrack): String {
    val base = track.baseUrl.replace(Regex("&fmt=[^&]*"), "")
    return base + (if ('?' in base) "&" else "?") + "fmt=json3" + if (track.translateToFrench) "&tlang=fr" else ""
}

class YoutubeCaptionFetcher : CaptionSource {
    override suspend fun fetch(videoId: String): VideoCaptions = withContext(Dispatchers.IO) {
        require(YoutubeUrl.videoId(videoId) == videoId)
        var title = ""
        var channel = ""
        for (client in innertubeClients) {
            val captions = runCatching {
                val response = JSONObject(post("https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
                    innertubePlayerRequest(client, videoId), client.userAgent))
                if (title.isBlank()) title = playerVideoTitle(response)
                if (channel.isBlank()) channel = response.optJSONObject("videoDetails")?.optString("author").orEmpty()
                selectCaptionTrack(response)?.let { YoutubeTimedTextParser.parse(get(timedTextUrl(it), client.userAgent)) }
            }.getOrNull()
            if (!captions.isNullOrEmpty()) return@withContext VideoCaptions(title, captions, channel)
        }
        // Last resort: the watch page. Its track URLs usually come back empty now, but it still gives the title.
        val response = extractPlayerResponse(get(YoutubeUrl.watchUrl(videoId), WEB_USER_AGENT))
        if (title.isBlank()) title = playerVideoTitle(response)
        if (channel.isBlank()) channel = response.optJSONObject("videoDetails")?.optString("author").orEmpty()
        val track = selectCaptionTrack(response) ?: return@withContext VideoCaptions(title, emptyList(), channel)
        val cues = runCatching { YoutubeTimedTextParser.parse(get(timedTextUrl(track), WEB_USER_AGENT)) }.getOrDefault(emptyList())
        VideoCaptions(title, cues, channel)
    }

    private fun get(url: String, userAgent: String): String = request(url, userAgent, null)

    private fun post(url: String, body: String, userAgent: String): String = request(url, userAgent, body)

    private fun request(url: String, userAgent: String, body: String?): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("User-Agent", userAgent)
        connection.setRequestProperty("Accept-Language", "fr,fr-FR;q=0.9")
        connection.setRequestProperty("Cookie", "SOCS=CAI")
        return try {
            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            check(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally { connection.disconnect() }
    }

    private companion object {
        const val WEB_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
    }
}
