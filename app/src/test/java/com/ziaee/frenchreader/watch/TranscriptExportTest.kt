package com.ziaee.frenchreader.watch

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptExportTest {
    @Test fun libraryTranscriptUsesExportParagraphFormat() {
        val cues = listOf(SubtitleCue(0, 1000, "[Musique] Bonjour."),
            SubtitleCue(3000, 4000, "Au revoir."))
        assertEquals("Bonjour.\n\nAu revoir.", transcriptOf(cues))
        assertEquals(transcriptParagraphs(cues), transcriptOf(cues))
        assertEquals("", transcriptOf(emptyList()))
    }

    @Test fun paragraphsSplitAtTwoSecondGap() {
        assertEquals("Bonjour.\n\nAu revoir.", transcriptParagraphs(listOf(
            SubtitleCue(0, 1000, "Bonjour."), SubtitleCue(3000, 4000, "Au revoir."))))
        assertEquals("Bonjour. Au revoir.", transcriptParagraphs(listOf(
            SubtitleCue(0, 1000, "Bonjour."), SubtitleCue(2999, 4000, "Au revoir."))))
    }

    @Test fun paragraphsHoldAtMostSixSentences() {
        val cues = (0..6).map { SubtitleCue(it * 1000L, it * 1000L + 900, "Bonjour.") }
        assertEquals(List(6) { "Bonjour." }.joinToString(" ") + "\n\nBonjour.", transcriptParagraphs(cues))
    }

    @Test fun markersAndWhitespaceAreRemoved() {
        assertEquals("Bonjour tout le monde.", transcriptParagraphs(listOf(
            SubtitleCue(0, 100, "[Musique]"),
            SubtitleCue(100, 1000, " Bonjour  [Applaudissements]\n tout le monde. "))))
        assertEquals("", transcriptParagraphs(listOf(SubtitleCue(0, 100, "[Musique]"))))
    }

    @Test fun srtPreservesMultilineTextAndLongTimestamps() {
        assertEquals("1\n01:02:03,004 --> 01:02:05,006\nBonjour\nle monde.\n\n",
            transcriptSrt(listOf(SubtitleCue(3723004, 3725006, "Bonjour\nle monde."))))
        assertEquals("", transcriptSrt(emptyList()))
    }

    @Test fun srtNumbersOriginalCues() {
        assertEquals("1\n00:00:00,000 --> 00:00:01,000\n[Musique]\n\n" +
            "2\n00:00:01,001 --> 00:00:02,010\nBonjour.\n\n",
            transcriptSrt(listOf(SubtitleCue(0, 1000, "[Musique]"), SubtitleCue(1001, 2010, "Bonjour."))))
    }

    @Test fun fileNamesAreSafeAndHaveFallback() {
        assertEquals("Bonjour_monde_.srt", transcriptFileName("Bonjour/monde?"))
        assertEquals("YouTube.srt", transcriptFileName(" ... "))
    }
}
