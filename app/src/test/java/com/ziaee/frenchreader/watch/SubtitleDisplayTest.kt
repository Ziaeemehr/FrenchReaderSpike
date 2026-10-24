package com.ziaee.frenchreader.watch

import com.ziaee.frenchreader.data.VocabStatus
import org.junit.Assert.*
import org.junit.Test

class SubtitleDisplayTest {
    @Test fun repeatedFragmentsHaveDistinctRanges() {
        val merged = mergeSentenceUnits(listOf(SubtitleCue(0, 500, "oui"), SubtitleCue(500, 1000, "oui !")))
        assertEquals(listOf(0 until 3, 4 until 9), merged.cueTextRanges)
        assertEquals("oui !", merged.units[0].text.substring(merged.cueTextRanges[1]!!))
    }

    @Test fun droppedNoiseHasNoRangeAndMixedNoiseUsesCleanOffsets() {
        val merged = mergeSentenceUnits(listOf(SubtitleCue(0, 500, " Salut [bruit]\n toi "),
            SubtitleCue(500, 600, "[musique]"), SubtitleCue(600, 1000, "encore.")))
        assertEquals("Salut toi encore.", merged.units.single().text)
        assertEquals(listOf(0 until 9, null, 10 until 17), merged.cueTextRanges)
        assertEquals(0, merged.unitIndex(1))
    }

    @Test fun noiseOutsideSentencesStaysStandalone() {
        val merged = mergeSentenceUnits(listOf(
            SubtitleCue(0, 100, "[musique]"),
            SubtitleCue(100, 500, "Bonjour."),
            SubtitleCue(500, 600, "[bruit]"),
            SubtitleCue(600, 1000, "Salut."),
            SubtitleCue(1000, 1100, "[musique]")
        ))
        assertEquals(listOf(-1, 0, -1, 1, -1), merged.cueUnitIndices)
        assertNull(merged.cueTextRanges[2])
    }

    @Test fun sentenceBoundariesResetRangesAndManualSentenceIsEntirelyActive() {
        val merged = mergeSentenceUnits(listOf(SubtitleCue(0, 500, "Bonjour !"), SubtitleCue(500, 1000, "Au revoir.")))
        assertEquals(listOf(0 until 9, 0 until 10), merged.cueTextRanges)
        assertEquals(listOf(0, 1), merged.cueUnitIndices)
        assertTrue(mergeSentenceUnits(emptyList()).cueTextRanges.isEmpty())
    }

    @Test fun durationAndGapBoundariesResetEmphasisOffsets() {
        val cues = listOf(SubtitleCue(0, 6000, "un"), SubtitleCue(6000, 12000, "deux"),
            SubtitleCue(12000, 13000, "trois"), SubtitleCue(15000, 16000, "quatre"))
        val merged = mergeSentenceUnits(cues)
        assertEquals(listOf(0, 0, 1, 2), merged.cueUnitIndices)
        assertEquals(listOf(0 until 2, 3 until 7, 0 until 5, 0 until 6), merged.cueTextRanges)
    }

    @Test fun savedSpansUseReaderNormalizationElisionAndLongestPhrase() {
        val text = "L’été, prendre soin de l’été; étéx."
        val statuses = mapOf("ete" to VocabStatus.NEW, "prendre soin" to VocabStatus.LEARNING)
        val matches = subtitleVocabMatches(text, statuses)
        assertEquals(listOf("été", "prendre soin", "été"), matches.map { text.substring(it.range) })
        assertEquals(listOf(VocabStatus.NEW, VocabStatus.LEARNING, VocabStatus.NEW), matches.map { it.status })
        assertTrue(subtitleVocabMatches(text, emptyMap()).isEmpty())
    }

    @Test fun savedSpansReflectUpdatedStatus() {
        for (status in VocabStatus.entries) {
            assertEquals(status, subtitleVocabMatches("Salut", mapOf("salut" to status)).single().status)
        }
    }
}
