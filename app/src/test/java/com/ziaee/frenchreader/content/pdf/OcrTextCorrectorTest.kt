package com.ziaee.frenchreader.content.pdf

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class OcrTextCorrectorTest {
    private val corrector = OcrTextCorrector(setOf("maison", "vieux", "conseille", "très", "continent", "porte", "monnaie"), setOf("très"))
    @Test fun fixesConfusionsAndPreservesCase() {
        assertEquals("maison vieux conseille très", corrector.correct("rnaison vieux conseilIe trés").text)
        assertEquals("MAISON", corrector.correct("RNAISON").text)
        val result = corrector.correct("rnaison")
        assertEquals("maison", result.text.substring(result.changes.single().start, result.changes.single().end))
        assertEquals("rnaison", result.revert(result.changes.single()).text)
    }
    @Test fun preservesParagraphsAndNormalizesFrenchTypography() {
        assertEquals("continent ici\n\n« texte » ; oui ! (bien), fin.",
            corrector.correct("conti-\nnent ici\n\n«texte»; oui! (bien) , fin .").text)
        assertEquals("porte-monnaie", corrector.correct("porte-\nmonnaie").text)
        assertEquals("maison", corrector.correct("*|maison").text)
    }
    @Test fun skipsHighConfidenceProperNamesAndDigitTokens() {
        assertEquals("rnaison", corrector.correct("rnaison", listOf(OcrTokenConfidence(0,7,.95f))).text)
        assertEquals("Ici Rnaison 5rnaison nnur", corrector.correct("Ici Rnaison 5rnaison nnur").text)
    }
    @Test fun refusesAmbiguousCandidates() {
        assertEquals("bate", OcrTextCorrector(setOf("date", "rate"), emptySet()).correct("bate").text)
    }
    @Test fun diffAndRevertTrackRepeatedWordsAndShiftedRanges() {
        val result = ocrWordDiff("le chat et le chat", "le chien et le chat")
        assertEquals(1, result.changes.size)
        assertEquals("le chat et le chat", result.revert(result.changes.single()).text)
    }
    @Test fun adjacentChangesRevertIndependentlyAndShiftLaterOffsets() {
        val result = corrector.correct("rnaison trés")
        assertEquals(2, result.changes.size)
        val reverted = result.revert(result.changes.first())
        assertEquals("rnaison très", reverted.text)
        val later = reverted.changes.single()
        assertEquals("très", reverted.text.substring(later.start, later.end))
        assertEquals("rnaison trés", reverted.revert(later).text)
    }
    @Test fun joinedWordRetainsItsOriginalOcrForReversion() {
        val result = corrector.correct("conti-\nnent")
        assertEquals("continent", result.text)
        assertEquals("conti-\nnent", result.changes.single().original)
        assertEquals("conti-\nnent", result.revert(result.changes.single()).text)
    }
    @Test fun weightedDistanceHandlesSymmetricMultiLetterConfusions() {
        for ((a,b) in listOf("rn" to "m", "nn" to "m", "cl" to "d", "li" to "h", "ii" to "ü", "vv" to "w")) {
            assertEquals(.2, weightedOcrDistance(a,b), .001)
            assertEquals(.2, weightedOcrDistance(b,a), .001)
        }
        assertEquals(.5, weightedOcrDistance("é","è"), .001)
        assertTrue(weightedOcrDistance("nnur", "pour") > 1.0)
    }
    @Test fun realLexiconTimingSanity() {
        val root = sequenceOf(File("src/main/assets/lexicon"), File("app/src/main/assets/lexicon")).first { it.exists() }
        val engine = realEngine(root)
        val start = System.nanoTime()
        val text = List(500) { listOf("rnaison", "vieux", "conseilIe", "trés", "nnur")[it % 5] }.joinToString(" ")
        val result = engine.correct(text)
        assertTrue(result.text.take(120), result.text.contains("maison vieux conseille très"))
        assertTrue("500 words took too long", (System.nanoTime() - start) / 1_000_000 < 2000)
    }

    /** Mirrors LemmaLexicon: forms plus their lemmas, since infinitives appear only as lemmas. */
    private fun realEngine(root: File): OcrTextCorrector {
        val forms = HashSet<String>()
        File(root, "lemmas.tsv").forEachLine { line ->
            forms += line.substringBefore('\t')
            line.substringAfter('\t', "").split(',').filter { it.isNotBlank() }.forEach { forms += it.trim() }
        }
        return OcrTextCorrector(forms, File(root, "common_lemmas.txt").readLines().toSet())
    }

    @Test fun correctWordsAreNeverChanged() {
        val root = sequenceOf(File("src/main/assets/lexicon"), File("app/src/main/assets/lexicon")).first { it.exists() }
        val text = "Il lui conseille de visiter le vieux quartier et de goûter la cuisine locale. " +
            "Elle prépare sa valise avec soin et appelle son frère pour lui annoncer la nouvelle."
        val result = realEngine(root).correct(text)
        assertEquals(emptyList<OcrChange>(), result.changes)
        assertEquals(text, result.text)
    }
}
