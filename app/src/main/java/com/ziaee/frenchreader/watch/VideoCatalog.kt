package com.ziaee.frenchreader.watch

import com.ziaee.frenchreader.resources.DefaultResource
import java.io.StringReader
import java.text.Normalizer
import java.util.Locale
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
