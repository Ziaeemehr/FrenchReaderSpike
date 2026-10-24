package com.ziaee.frenchreader.watch

import com.ziaee.frenchreader.resources.DefaultResource
import com.ziaee.frenchreader.resources.ResourceCategory
import org.junit.Assert.*
import org.junit.Test

class VideoPickerTest {
    @Test fun searchIgnoresCaseAndAccents() {
        assertEquals("francais ecole", normalizeVideoSearch("FRANÇAIS École"))
        assertEquals(normalizeVideoSearch("é"), normalizeVideoSearch("e\u0301"))
    }

    @Test fun levelsOverlapInclusiveRanges() {
        assertTrue(videoLevelOverlaps("B1–B2", "B1"))
        assertTrue(videoLevelOverlaps("A2–B2", "A1–A2"))
        assertTrue(videoLevelOverlaps("B2–C1", "C1+"))
        assertTrue(videoLevelOverlaps("C2", "C1+"))
        assertFalse(videoLevelOverlaps("B1–B2", "A1–A2"))
        assertFalse(videoLevelOverlaps(null, "B1"))
        assertTrue(videoLevelOverlaps(null, null))
    }

    @Test fun recentUpdatesMoveToFrontDeduplicateAndCap() {
        val old = (0..29).map { RecentVideo("id$it", "Title $it", "Channel", it.toLong()) }
        val updated = updateRecentVideos(old, RecentVideo("id12", "New title", "Channel", 4000))
        assertEquals(30, updated.size)
        assertEquals("id12", updated.first().videoId)
        assertEquals(4000L, updated.first().positionMs)
        assertEquals(1, updated.count { it.videoId == "id12" })
        val inserted = updateRecentVideos(updated, RecentVideo("new", "New", "", -10))
        assertEquals(30, inserted.size)
        assertEquals(0L, inserted.first().positionMs)
        assertFalse(inserted.any { it.videoId == "id29" })
        assertEquals("Channel", updateRecentVideos(old, RecentVideo("id12", "", "", 50)).first().channel)
        assertEquals("Title 12", updateRecentVideos(old, RecentVideo("id12", "", "", 50)).first().title)
    }

    @Test fun parsesOnlyEntryFieldsAndDecodesEntities() {
        val xml = requireNotNull(javaClass.getResource("/watch/channel.xml")).readText()
        val videos = parseYoutubeFeed(xml)
        assertEquals(2, videos.size)
        assertEquals("abcdefghijk", videos[0].videoId)
        assertEquals("École & culture", videos[0].title)
        assertEquals("2026-10-23T10:00:00+00:00", videos[0].published)
        assertEquals("https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg", videos[0].thumbnail)
        assertEquals("https://i.ytimg.com/vi/lmnopqrstuv/mqdefault.jpg", videos[1].thumbnail)
    }
    @Test fun emptyFeedIsValidButErrorPagesAreRejected() {
        assertTrue(parseYoutubeFeed("<feed xmlns=\"http://www.w3.org/2005/Atom\"/>").isEmpty())
        assertThrows(IllegalArgumentException::class.java) { parseYoutubeFeed("<html><body>Offline</body></html>") }
    }

    @Test fun feedIgnoresMalformedVideoIdsAndDuplicateEntries() {
        val entry = "<entry><yt:videoId>abcdefghijk</yt:videoId><title>First</title></entry>"
        val xml = "<feed xmlns:yt=\"http://www.youtube.com/xml/schemas/2015\">" + entry + entry +
            "<entry><yt:videoId>invalid</yt:videoId><title>Invalid</title></entry></feed>"
        assertEquals(listOf("abcdefghijk"), parseYoutubeFeed(xml).map { it.videoId })
    }

    @Test fun channelTitleMatchesRankAheadOfDescriptionAndFeedMatchesStably() {
        fun channel(title: String, id: String, description: String = "", level: String = "B1") =
            DefaultResource(title, "https://www.youtube.com/channel/$id", ResourceCategory.YOUTUBE_LESSONS,
                0L, level, mapOf("en" to description))
        val description = channel("Lessons", "description", "French École lessons")
        val title = channel("École française", "title")
        val feed = channel("Culture", "feed")
        val title2 = channel("Une autre école", "title2")
        val channels = listOf(description, title, feed, title2)
        val feeds = mapOf("feed" to listOf(ChannelVideo("abcdefghijk", "Une école", "", "")))
        assertEquals(listOf(title, title2, description, feed), searchVideoChannels(channels, " ECOLE ", null, feeds))
        assertEquals(channels, searchVideoChannels(channels, "", null, feeds))
        assertTrue(searchVideoChannels(channels, "école", "C1+", feeds).isEmpty())
        assertTrue(searchVideoChannels(channels, "introuvable", null, feeds).isEmpty())
    }

    @Test fun detailSearchUsesOnlyVideoTitlesAndKeepsAnUnmatchedQueryEmpty() {
        val videos = listOf(ChannelVideo("abcdefghijk", "Une école", "", ""),
            ChannelVideo("lmnopqrstuv", "La cuisine", "", ""))
        assertEquals(listOf(videos.first()), searchChannelVideos(videos, " ECOLE "))
        assertEquals(videos, searchChannelVideos(videos, ""))
        assertTrue(searchChannelVideos(videos, "channel title").isEmpty())
    }
}
