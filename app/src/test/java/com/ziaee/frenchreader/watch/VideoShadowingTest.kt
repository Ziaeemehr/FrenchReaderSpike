package com.ziaee.frenchreader.watch

import org.junit.Assert.*
import org.junit.Test

class VideoShadowingTest {
    @Test fun mergesFragmentsWithoutChangingOriginals() {
        val cues = listOf(SubtitleCue(0, 1000, "Alors, là on est en"), SubtitleCue(1000, 2500, "plein été."))
        assertEquals(listOf(SubtitleCue(0, 2500, "Alors, là on est en plein été.")), mergeIntoSentences(cues))
        assertEquals("Alors, là on est en", cues.first().text)
    }

    @Test fun punctuationAndClosingQuotesEndUnits() {
        for (ending in listOf(".", "!", "?", "…", " ! »", " ?\"")) {
            assertEquals(2, mergeIntoSentences(listOf(SubtitleCue(0, 500, "Salut$ending"), SubtitleCue(500, 1000, "Suite"))).size)
        }
    }

    @Test fun gapAndDurationBoundaries() {
        assertEquals(1, mergeIntoSentences(listOf(SubtitleCue(0, 1000, "un"), SubtitleCue(2200, 3000, "deux"))).size)
        assertEquals(2, mergeIntoSentences(listOf(SubtitleCue(0, 1000, "un"), SubtitleCue(2201, 3000, "deux"))).size)
        val cues = listOf(SubtitleCue(0, 6000, "un"), SubtitleCue(6000, 12000, "deux"), SubtitleCue(12000, 13000, "trois"))
        assertEquals(listOf(SubtitleCue(0, 12000, "un deux"), cues.last()), mergeIntoSentences(cues))
        assertEquals(3, mergeIntoSentences(cues, 6000).size)
    }

    @Test fun dropsNonSpeechAndNormalizesWhitespace() {
        assertEquals(emptyList<SubtitleCue>(), mergeIntoSentences(listOf(SubtitleCue(0, 1000, "[musique]"))))
        assertEquals(emptyList<SubtitleCue>(), mergeIntoSentences(emptyList()))
        assertEquals(listOf(SubtitleCue(0, 1000, "Salut toi !")), mergeIntoSentences(listOf(SubtitleCue(0, 1000, "Salut [Applaudissements]\n toi !"))))
    }

    @Test fun oversizedIndividualCueIsPreserved() {
        val cue = SubtitleCue(0, 15000, "Une longue ligne")
        assertEquals(listOf(cue), mergeIntoSentences(listOf(cue)))
    }

    @Test fun pauseRequiresOwnedPlaybackAndAnObservedSeek() {
        val cue = SubtitleCue(1000, 2000, "Salut")
        assertFalse(shouldAutoPause(2150, cue, false, true))
        assertFalse(shouldAutoPause(9000, cue, true, false))
        assertFalse(shouldAutoPause(2149, cue, true, true))
        assertTrue(shouldAutoPause(2150, cue, true, true))
        // After the controller consumes the pause, subsequent ticks and normal playback are inert.
        assertFalse(shouldAutoPause(2350, cue, false, false))
        assertFalse(shouldAutoPause(9000, null, true, true))
    }
    @Test fun sentenceLookupUsesOriginalCueMembership() {
        val cues = listOf(SubtitleCue(0, 500, "Voici"), SubtitleCue(500, 1000, "[musique]"),
            SubtitleCue(1000, 1500, "une phrase."), SubtitleCue(1500, 2000, "La suivante !"))
        val merged = mergeSentenceUnits(cues)
        // Noise inside a sentence belongs to it (display/translation/navigation) but has no text range.
        assertEquals(listOf(0, 0, 0, 1), merged.cueUnitIndices)
        assertEquals("Voici une phrase.", merged.units[merged.unitIndex(2)].text)
        assertEquals(0, merged.unitIndex(1))
        assertEquals(null, merged.cueTextRanges[1])
        assertEquals(-1, merged.unitIndex(-1))
        assertEquals(-1, merged.unitIndex(4))
        assertEquals(-1, mergeSentenceUnits(emptyList()).unitIndex(0))
    }

    @Test fun membershipDistinguishesCuesWithTheSameTimestamp() {
        val merged = mergeSentenceUnits(listOf(SubtitleCue(0, 500, "Salut"),
            SubtitleCue(0, 500, "[musique]"), SubtitleCue(500, 1000, "toi !")))
        assertEquals(listOf(0, 0, 0), merged.cueUnitIndices)
        assertEquals(null, merged.cueTextRanges[1])
    }
}
