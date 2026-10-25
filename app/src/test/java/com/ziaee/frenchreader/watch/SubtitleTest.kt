package com.ziaee.frenchreader.watch

import org.junit.Assert.*
import org.junit.Test

class SubtitleTest {
    @Test fun srtHandlesBomMultilineAndEntities() {
        val cues = SrtParser.parse("\uFEFF1\r\n00:00:01,200 --> 00:00:03,000\r\n<b>Salut</b> &amp; toi\r\nça va ?\r\n\r\n2\r\ninvalid\r\nignored")
        assertEquals(listOf(SubtitleCue(1200, 3000, "Salut & toi\nça va ?")), cues)
    }

    @Test fun vttSkipsMetadataAndStripsInlineTimestamps() {
        val text = """
            WEBVTT
            Kind: captions

            NOTE hidden
            ignore me

            STYLE
            ::cue { color: lime; }

            first
            00:01.000 --> 00:02.500 align:start position:10%
            <c.green>l’école</c> <00:00:01.500>&#39; &eacute;

            00:03.000 --> 00:04.000
            fin
        """.trimIndent()
        assertEquals(listOf(SubtitleCue(1000, 2500, "l’école ' é"), SubtitleCue(3000, 4000, "fin")), VttParser.parse(text))
    }

    @Test fun json3MergesSegmentsAndClipsRollingOverlap() {
        val input = """{"events":[
            {"tStartMs":0,"dDurationMs":2500,"segs":[{"utf8":"Bonjour "},{"utf8":"à tous"}]},
            {"tStartMs":1000,"dDurationMs":2000,"segs":[{"utf8":"Bonjour à tous !"}]},
            {"tStartMs":1200,"dDurationMs":10,"segs":[{"utf8":"\n"}]},
            {"tStartMs":3000,"dDurationMs":500,"segs":[]}] }"""
        assertEquals(listOf(SubtitleCue(0, 1000, "Bonjour à tous"), SubtitleCue(1000, 3000, "Bonjour à tous !")), YoutubeTimedTextParser.parse(input))
    }

    @Test fun cueLookupUsesExclusiveEndAndHandlesGaps() {
        val cues = listOf(SubtitleCue(100, 200, "a"), SubtitleCue(300, 400, "b"))
        assertEquals(-1, findCueIndex(cues, 99))
        assertEquals(0, findCueIndex(cues, 100))
        assertEquals(-1, findCueIndex(cues, 200))
        assertEquals(-1, findCueIndex(cues, 299))
        assertEquals(1, findCueIndex(cues, 300))
        assertEquals(-1, findCueIndex(cues, 400))
        assertEquals(-1, findCueIndex(emptyList(), 100))
    }

    @Test fun tokenizerKeepsFrenchApostrophes() {
        assertEquals(listOf("l’", "école", " ", "qu'", "il", " ", "aime", ", ", "aujourd’hui", "."),
            tokenizeSubtitle("l’école qu'il aime, aujourd’hui.").map { it.text })
    }
}

class SubtitleOverlapTest {
    @Test fun simultaneousSrtCuesAreKeptTogether() {
        val srt = "1\n00:00:01,000 --> 00:00:03,000\n- Salut !\n\n2\n00:00:01,000 --> 00:00:03,500\n- Bonjour.\n"
        val cues = SrtParser.parse(srt)
        org.junit.Assert.assertEquals(1, cues.size)
        org.junit.Assert.assertEquals("- Salut !\n- Bonjour.", cues[0].text)
        org.junit.Assert.assertEquals(3500L, cues[0].endMs)
    }
}
