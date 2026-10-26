package com.ziaee.frenchreader.watch

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelPageFallbackTest {
    private val today = LocalDate.of(2026, 10, 27)
    private val json = """{"contents":{"richGridRenderer":{"contents":[
        {"richItemRenderer":{"content":{"compactVideoRenderer":{"videoId":"--vNiuWF8N4","title":{"runs":[{"text":"Tintin & "},{"text":"Le Lotus"}]},"publishedTimeText":{"runs":[{"text":"5 hours ago"}]}}}}},
        {"richItemRenderer":{"content":{"videoRenderer":{"videoId":"roQgPYL06_4","title":{"simpleText":"GeoGuessr"},"publishedTimeText":{"simpleText":"2 weeks ago"}}}}},
        {"richItemRenderer":{"content":{"videoRenderer":{"videoId":"roQgPYL06_4","title":{"simpleText":"duplicate"}}}}}]}}}"""

    @Test fun readsTheJsonScriptElement() {
        val videos = parseYoutubeChannelPage("""<script id="yt-initial-data" type="application/json">$json</script>""", today)
        assertEquals(listOf("--vNiuWF8N4", "roQgPYL06_4"), videos.map { it.videoId })
        assertEquals("Tintin & Le Lotus", videos[0].title)
        assertEquals("2026-10-27", videos[0].published)
        assertEquals("2026-10-13", videos[1].published)
    }

    @Test fun readsTheMobileStringLiteral() {
        val escaped = json.map { if (it == '{' || it == '}' || it == '"') "\\x%02x".format(it.code) else it.toString() }.joinToString("")
        val videos = parseYoutubeChannelPage("<script>var ytInitialData = '$escaped';</script>", today)
        assertEquals(2, videos.size)
    }

    @Test fun readsTheDesktopObjectLiteral() {
        assertEquals(2, parseYoutubeChannelPage("<script>var ytInitialData = $json;</script>", today).size)
    }

    @Test fun pageWithoutDataGivesNothing() {
        assertTrue(parseYoutubeChannelPage("<html>consent</html>", today).isEmpty())
    }

    @Test fun fallbackListRoundTripsThroughTheFeedCache() {
        val videos = parseYoutubeChannelPage("<script>var ytInitialData = $json;</script>", today)
        assertEquals(videos, parseYoutubeFeed(youtubeFeedXml(videos)))
    }

    @Test fun relativeDates() {
        assertEquals(today.minusMonths(3), relativeDay("3 months ago", today))
        assertEquals(today.minusYears(1), relativeDay("Streamed 1 year ago", today))
        assertEquals(null, relativeDay("", today))
    }
}
