package com.ziaee.frenchreader.watch

import com.ziaee.frenchreader.resources.DefaultResource
import java.io.StringReader
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

internal fun channelId(url: String): String = url.substringAfterLast('/').substringBefore('?')

data class ChannelVideo(val videoId: String, val title: String, val published: String, val thumbnail: String)
data class RecentVideo(val videoId: String, val title: String, val channel: String, val positionMs: Long)

fun normalizeVideoSearch(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

/** Missing levels match All only; a range includes its intermediate CEFR levels. */
fun videoLevelOverlaps(level: String?, filter: String?): Boolean {
    if (filter == null) return true
    fun range(value: String): IntRange? {
        val levels = listOf("A1", "A2", "B1", "B2", "C1", "C2")
        val found = Regex("[ABC][12]").findAll(value.uppercase(Locale.ROOT)).map { levels.indexOf(it.value) }.toList()
        if (found.isEmpty()) return null
        return found.min()..if (value.contains('+')) 5 else found.max()
    }
    val channel = level?.let(::range) ?: return false
    val selected = range(filter) ?: return false
    return channel.first <= selected.last && selected.first <= channel.last
}

fun updateRecentVideos(old: List<RecentVideo>, entry: RecentVideo): List<RecentVideo> {
    val previous = old.firstOrNull { it.videoId == entry.videoId }
    val merged = entry.copy(title = entry.title.ifBlank { previous?.title.orEmpty() },
        channel = entry.channel.ifBlank { previous?.channel.orEmpty() }, positionMs = entry.positionMs.coerceAtLeast(0))
    return (listOf(merged) + old.filterNot { it.videoId == entry.videoId }).distinctBy { it.videoId }.take(30)
}

fun parseYoutubeFeed(xml: String): List<ChannelVideo> {
    val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
    parser.setInput(StringReader(xml))
    val videos = mutableListOf<ChannelVideo>()
    var entryDepth = -1
    var id = ""; var title = ""; var published = ""; var thumbnail = ""
    var isFeed = false
    while (parser.eventType != XmlPullParser.END_DOCUMENT) {
        if (parser.eventType == XmlPullParser.START_TAG) {
            if (parser.depth == 1) isFeed = parser.name == "feed"
            when {
                parser.name == "entry" && parser.depth == 2 -> {
                    entryDepth = parser.depth; id = ""; title = ""; published = ""; thumbnail = ""
                }
                entryDepth >= 0 -> when {
                    parser.name == "videoId" && parser.namespace == "http://www.youtube.com/xml/schemas/2015" -> id = parser.nextText()
                    parser.depth == entryDepth + 1 && parser.name == "title" -> title = parser.nextText()
                    parser.depth == entryDepth + 1 && parser.name == "published" -> published = parser.nextText()
                    parser.name == "thumbnail" && parser.namespace == "http://search.yahoo.com/mrss/" -> thumbnail = parser.getAttributeValue(null, "url").orEmpty()
                }
            }
        } else if (parser.eventType == XmlPullParser.END_TAG && parser.name == "entry" && parser.depth == entryDepth) {
            if (Regex("[A-Za-z0-9_-]{11}").matches(id)) videos += ChannelVideo(id, title, published,
                thumbnail.ifBlank { "https://i.ytimg.com/vi/$id/mqdefault.jpg" })
            entryDepth = -1
        }
        parser.next()
    }
    require(isFeed) { "Invalid YouTube feed" }
    return videos.distinctBy { it.videoId }
}

/** Stable ranking preserves curated order within title matches and within secondary matches. */
fun searchVideoChannels(
    channels: List<DefaultResource>, query: String, level: String?, feeds: Map<String, List<ChannelVideo>>
): List<DefaultResource> {
    val search = normalizeVideoSearch(query.trim())
    return channels.filter { channel ->
        videoLevelOverlaps(channel.level, level) && (search.isBlank() ||
            (listOf(channel.title, channel.level.orEmpty()) + channel.description.values +
                feeds[channelId(channel.url)].orEmpty().map { it.title })
                .any { normalizeVideoSearch(it).contains(search) })
    }.sortedBy { if (search.isBlank() || normalizeVideoSearch(it.title).contains(search)) 0 else 1 }
}

fun searchChannelVideos(videos: List<ChannelVideo>, query: String): List<ChannelVideo> {
    val search = normalizeVideoSearch(query.trim())
    return videos.filter { search.isBlank() || normalizeVideoSearch(it.title).contains(search) }
}

/**
 * Videos listed on a channel's /videos page, read from its embedded ytInitialData. YouTube's RSS feed
 * returns 404 for many channels at times; this page carries the same latest uploads. The page gives
 * only relative dates ("3 days ago", requested with hl=en), so [today] turns them into approximate days.
 */
fun parseYoutubeChannelPage(html: String, today: LocalDate): List<ChannelVideo> {
    val data = initialData(html) ?: return emptyList()
    val videos = mutableListOf<ChannelVideo>()
    fun text(value: Any?): String = (value as? JSONObject)?.let { title ->
        title.optString("simpleText").ifBlank {
            title.optJSONArray("runs")?.let { runs -> (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text").orEmpty() } }.orEmpty()
        }
    }.orEmpty()
    fun walk(node: Any?) {
        when (node) {
            is JSONObject -> node.keys().forEach { key ->
                val value = node.opt(key)
                if (key.endsWith("ideoRenderer") && value is JSONObject && value.has("videoId")) {
                    val id = value.optString("videoId")
                    if (Regex("[A-Za-z0-9_-]{11}").matches(id)) videos += ChannelVideo(id, text(value.opt("title")),
                        relativeDay(text(value.opt("publishedTimeText")), today)?.toString().orEmpty(),
                        "https://i.ytimg.com/vi/$id/mqdefault.jpg")
                } else if (key == "lockupViewModel" && value is JSONObject) {
                    val id = value.optString("contentId")
                    val metadata = value.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
                    if (Regex("[A-Za-z0-9_-]{11}").matches(id)) videos += ChannelVideo(id,
                        metadata?.optJSONObject("title")?.optString("content").orEmpty(), "",
                        "https://i.ytimg.com/vi/$id/mqdefault.jpg")
                } else walk(value)
            }
            is JSONArray -> for (index in 0 until node.length()) walk(node.opt(index))
        }
    }
    walk(data)
    return videos.distinctBy { it.videoId }
}

/** The page embeds ytInitialData as a JSON script element, a JS string literal (mobile) or a JS object literal. */
private fun initialData(html: String): JSONObject? = runCatching {
    Regex("""<script id="yt-initial-data"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL).find(html)?.let {
        return@runCatching JSONObject(it.groupValues[1])
    }
    val quoted = html.indexOf("var ytInitialData = '")
    if (quoted >= 0) {
        val start = quoted + "var ytInitialData = '".length
        return@runCatching JSONObject(decodeJsString(html.substring(start, html.indexOf("';", start))))
    }
    val literal = html.indexOf("var ytInitialData = {")
    if (literal >= 0) JSONTokener(html.substring(literal + "var ytInitialData = ".length)).nextValue() as JSONObject else null
}.getOrNull()

private fun decodeJsString(value: String): String {
    val out = StringBuilder(value.length)
    var index = 0
    while (index < value.length) {
        val char = value[index]
        if (char != '\\' || index + 1 >= value.length) { out.append(char); index++; continue }
        when (val next = value[index + 1]) {
            'x' -> { out.append(value.substring(index + 2, index + 4).toInt(16).toChar()); index += 4 }
            'u' -> { out.append(value.substring(index + 2, index + 6).toInt(16).toChar()); index += 6 }
            'n' -> { out.append('\n'); index += 2 }
            't' -> { out.append('\t'); index += 2 }
            else -> { out.append(next); index += 2 }
        }
    }
    return out.toString()
}

internal fun relativeDay(text: String, today: LocalDate): LocalDate? {
    val match = Regex("""(?:Streamed |Premiered )?(\d+) (second|minute|hour|day|week|month|year)s? ago""").find(text) ?: return null
    val amount = match.groupValues[1].toLong()
    return when (match.groupValues[2]) {
        "second", "minute", "hour" -> today
        "day" -> today.minusDays(amount)
        "week" -> today.minusWeeks(amount)
        "month" -> today.minusMonths(amount)
        else -> today.minusYears(amount)
    }
}

/** The same list as a minimal Atom feed, so the RSS cache and [parseYoutubeFeed] keep working unchanged. */
fun youtubeFeedXml(videos: List<ChannelVideo>): String = buildString {
    fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    append("""<?xml version="1.0" encoding="UTF-8"?><feed xmlns="http://www.w3.org/2005/Atom" xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns:media="http://search.yahoo.com/mrss/">""")
    videos.forEach { video ->
        append("<entry><yt:videoId>${video.videoId}</yt:videoId><title>${escape(video.title)}</title>")
        append("<published>${escape(video.published)}</published><media:group><media:thumbnail url=\"${escape(video.thumbnail)}\"/></media:group></entry>")
    }
    append("</feed>")
}
