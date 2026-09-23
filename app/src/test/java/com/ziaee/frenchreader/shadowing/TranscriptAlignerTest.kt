package com.ziaee.frenchreader.shadowing

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptAlignerTest {
    @Test fun exactMatch_allWordsMatched() {
        val r = TranscriptAligner.align("Le chat dort.", "le chat dort")
        assertEquals(3, r.total); assertEquals(3, r.matched)
        assertEquals(listOf("Le", "chat", "dort"), r.words.map { it.word })
    }

    @Test fun accentsAndCaseIgnored() {
        val r = TranscriptAligner.align("Élève à l'école", "eleve a l ecole")
        assertEquals(r.total, r.matched)
    }

    @Test fun elisionSplit_straightAndCurlyApostrophe() {
        assertEquals(listOf("l", "avion"), TranscriptAligner.normalizeTokens("l'avion"))
        assertEquals(listOf("l", "avion"), TranscriptAligner.normalizeTokens("l’avion"))
        val r = TranscriptAligner.align("J'aime l’avion", "j'aime l'avion")
        assertEquals(4, r.total); assertEquals(4, r.matched)
        assertEquals(listOf("J'", "aime", "l’", "avion"), r.words.map { it.word })
    }

    @Test fun hyphenatedWordsSplit() {
        assertEquals(listOf("peut", "etre"), TranscriptAligner.normalizeTokens("peut-être"))
        val r = TranscriptAligner.align("C'est-à-dire", "c'est à dire")
        assertEquals(r.total, r.matched)
    }

    @Test fun missingWordMarkedUnmatched() {
        val r = TranscriptAligner.align("une magnifique image dans un livre", "une image dans un livre")
        assertEquals(6, r.total); assertEquals(5, r.matched)
        assertEquals(false, r.words[1].matched)
    }

    @Test fun wrongWordMarkedUnmatched() {
        val r = TranscriptAligner.align("sur la forêt vierge", "sur la forêt vi")
        assertEquals(listOf(true, true, true, false), r.words.map { it.matched })
    }

    @Test fun extraSpokenWordsDoNotReduceScore() {
        val r = TranscriptAligner.align("le chat dort", "euh le chat euh dort")
        assertEquals(3, r.matched)
    }

    @Test fun emptyTranscript_nothingMatched() {
        val r = TranscriptAligner.align("le chat dort", "")
        assertEquals(3, r.total); assertEquals(0, r.matched)
    }

    @Test fun punctuationOnlyTokensDropped() {
        val r = TranscriptAligner.align("« Bonjour ! » — dit-il.", "bonjour dit il")
        assertEquals(listOf("Bonjour", "dit", "il"), r.words.map { it.word })
        assertEquals(3, r.matched)
    }
}
