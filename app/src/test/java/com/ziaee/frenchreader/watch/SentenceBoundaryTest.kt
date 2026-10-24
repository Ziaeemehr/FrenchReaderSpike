package com.ziaee.frenchreader.watch

import org.junit.Assert.*
import org.junit.Test

class SentenceBoundaryTest {
    @Test fun abbreviationsAndInitialsStayWithFollowingWords() {
        for (token in listOf("M.", "MM.", "Mme.", "Mmes.", "Mlle.", "Dr.", "Pr.", "St.", "Ste.",
                "cf.", "p.", "n°", "av.", "apr.", "J.-C.", "ex.", "J.", "É.")) {
            assertFalse(token, isSentenceEnd("Voici $token", "Dupont arrive."))
        }
    }

    @Test fun etcUsesFollowingToken() {
        assertFalse(isSentenceEnd("etc.", "puis on continue"))
        assertFalse(isSentenceEnd("etc.", "2 personnes"))
        assertTrue(isSentenceEnd("etc.", "Ensuite"))
        assertTrue(isSentenceEnd("etc.", ""))
    }

    @Test fun numbersAreNotSentenceBoundaries() {
        assertFalse(isSentenceEnd("3.5", "Ensuite"))
        assertFalse(isSentenceEnd("1.000", "Ensuite"))
        assertFalse(isSentenceEnd("3.", "5 euros"))
        assertFalse(isSentenceEnd("1.", "000 euros"))
        assertTrue(isSentenceEnd("Il coûte 3.5.", "Ensuite"))
    }

    @Test fun ellipsesRequireUppercaseLookahead() {
        for (ending in listOf("…", "...")) {
            assertTrue(isSentenceEnd("Alors$ending", "« Écoute"))
            assertFalse(isSentenceEnd("Alors$ending", "puis"))
            assertFalse(isSentenceEnd("Alors$ending", "2 fois"))
            assertFalse(isSentenceEnd("Alors$ending", ""))
        }
    }

    @Test fun punctuationAndQuotesStillEndSentences() {
        for (ending in listOf(".", "!", "?", ". »", "?\"", " ! »")) {
            assertTrue(isSentenceEnd("Terminé$ending", "Suite"))
        }
        assertFalse(isSentenceEnd("sans ponctuation", "Suite"))
    }

    @Test fun parserLooksPastWhitespaceSegmentsForEtc() {
        val json = """{"events":[{"tStartMs":0,"dDurationMs":1000,"segs":[
            {"utf8":"etc."},{"utf8":" ","tOffsetMs":300},{"utf8":"puis","tOffsetMs":500}]}]}"""
        assertEquals(listOf(SubtitleCue(0, 1000, "etc. puis")), YoutubeTimedTextParser.parse(json))
    }

    @Test fun parserAndMergerShareBoundaries() {
        for ((left, right, split) in listOf(
            Triple("M.", "Dupont arrive.", false),
            Triple("etc.", "2 exemples.", false),
            Triple("etc.", "Ensuite.", true),
            Triple("Alors...", "puis.", false),
            Triple("Alors…", "Écoute.", true),
            Triple("3.", "5 euros.", false),
            Triple("1.000", "euros.", false)
        )) {
            val expected = if (split) 2 else 1
            val json = """{"events":[{"tStartMs":0,"dDurationMs":1000,"segs":[
                {"utf8":"$left"},{"utf8":" $right","tOffsetMs":500}]}]}"""
            assertEquals("$left $right parser", expected, YoutubeTimedTextParser.parse(json).size)
            assertEquals("$left $right merger", expected, mergeIntoSentences(listOf(
                SubtitleCue(0, 500, left), SubtitleCue(500, 1000, right))).size)
        }
    }
}
