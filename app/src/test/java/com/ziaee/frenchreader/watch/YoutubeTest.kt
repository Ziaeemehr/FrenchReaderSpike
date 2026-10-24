package com.ziaee.frenchreader.watch

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class YoutubeTest {
    private val id = "dQw4w9WgXcQ"

    @Test fun parsesCommonLinksAndIds() {
        listOf(id, "https://www.youtube.com/watch?v=$id&t=30", "https://youtu.be/$id?si=test",
            "https://m.youtube.com/watch?feature=share&v=$id", "https://youtube.com/shorts/$id",
            "https://www.youtube.com/embed/$id", "youtu.be/$id").forEach { assertEquals(it, id, YoutubeUrl.videoId(it)) }
    }

    @Test fun rejectsOtherHostsAndMalformedIds() {
        listOf("", "abc", "https://youtube.com.evil.org/watch?v=$id", "https://evil.org/$id",
            "https://youtube.com/watch?v=short", "https://youtu.be/${id}extra", "ftp://youtube.com/watch?v=$id")
            .forEach { assertNull(it, YoutubeUrl.videoId(it)) }
    }

    private fun response() = JSONObject(javaClass.getResource("/watch/player.json")!!.readText())

    @Test fun prefersManualFrenchBeforeAsr() {
        val selected = selectCaptionTrack(response())!!
        assertEquals("fr-CA", selected.languageCode)
        assertFalse(selected.translateToFrench)
        assertEquals("Un titre français", playerVideoTitle(response()))
    }

    @Test fun prefersFrenchAsrBeforeTranslation() {
        val json = response()
        json.getJSONObject("captions").getJSONObject("playerCaptionsTracklistRenderer").getJSONArray("captionTracks").remove(2)
        assertEquals("fr", selectCaptionTrack(json)!!.languageCode)
        assertFalse(selectCaptionTrack(json)!!.translateToFrench)
    }

    @Test fun translatesOtherLanguageAndHandlesMissingTracks() {
        val json = response()
        val tracks = json.getJSONObject("captions").getJSONObject("playerCaptionsTracklistRenderer").getJSONArray("captionTracks")
        tracks.remove(2)
        tracks.remove(1)
        assertTrue(selectCaptionTrack(json)!!.translateToFrench)
        assertNull(selectCaptionTrack(JSONObject()))
    }

    @Test fun extractsBalancedPlayerResponseWithBracesInsideStrings() {
        val json = extractPlayerResponse("<script>var ytInitialPlayerResponse = {\"videoDetails\":{\"title\":\"a } { \\\" b\"}}; more();</script>")
        assertEquals("a } { \" b", playerVideoTitle(json))
    }
}

class YoutubeCaptionRequestTest {
    @Test fun timedTextUrlReplacesExistingFormatAndAddsTranslation() {
        val track = CaptionTrack("https://www.youtube.com/api/timedtext?v=abc&fmt=srv3&lang=en", "en", "", translateToFrench = true)
        assertEquals("https://www.youtube.com/api/timedtext?v=abc&lang=en&fmt=json3&tlang=fr", timedTextUrl(track))
    }

    @Test fun playerRequestCarriesClientAndVideo() {
        val json = JSONObject(innertubePlayerRequest(innertubeClients.first(), "kTSnYNClAao"))
        assertEquals("kTSnYNClAao", json.getString("videoId"))
        val client = json.getJSONObject("context").getJSONObject("client")
        assertEquals("ANDROID", client.getString("clientName"))
        assertEquals("fr", client.getString("hl"))
    }

    @Test fun wordOffsetsSplitSentencesInsideAnAsrEvent() {
        val cues = YoutubeTimedTextParser.parse(javaClass.getResource("/watch/sentence-offsets.json3")!!.readText())
        assertEquals(listOf(
            SubtitleCue(1000, 2200, "la suite."),
            SubtitleCue(2200, 5000, "La deuxième"),
            SubtitleCue(5000, 6000, "arrive.")
        ), cues)
        assertEquals(listOf(
            SubtitleCue(1000, 2200, "la suite."),
            SubtitleCue(2200, 6000, "La deuxième arrive.")
        ), mergeIntoSentences(cues))
    }

    @Test fun sentencePunctuationWithoutWordOffsetsKeepsEventIntact() {
        val cues = YoutubeTimedTextParser.parse("""
            {"events":[{"tStartMs":1000,"dDurationMs":4000,
              "segs":[{"utf8":"la suite. "},{"utf8":"La deuxième"}]}]}
        """.trimIndent())
        assertEquals(listOf(SubtitleCue(1000, 5000, "la suite. La deuxième")), cues)
    }

    @Test fun closingQuotesAndBracketsAllowSentenceSplits() {
        for (ending in listOf("!", "?", "…", ".»", ".]", ".”)")) {
            val json = JSONObject().put("events", org.json.JSONArray().put(
                JSONObject().put("tStartMs", 0).put("dDurationMs", 2000).put("segs",
                    org.json.JSONArray().put(JSONObject().put("utf8", "Salut$ending").put("tOffsetMs", 0))
                        .put(JSONObject().put("utf8", "Encore").put("tOffsetMs", 1000)))))
            assertEquals(2, YoutubeTimedTextParser.parse(json.toString()).size)
        }
    }

    @Test fun asrRollingEventsBecomeNonOverlappingCues() {
        val cues = YoutubeTimedTextParser.parse(javaClass.getResource("/watch/asr.json3")!!.readText())
        assertEquals(listOf("[musique]", "Alors, là on est en", "plein milieu de l'été", "et vous devriez l'écouter"), cues.map { it.text })
        assertEquals(6610L, cues[1].endMs)
        cues.zipWithNext().forEach { (a, b) -> assertTrue(a.endMs <= b.startMs) }
    }
}
