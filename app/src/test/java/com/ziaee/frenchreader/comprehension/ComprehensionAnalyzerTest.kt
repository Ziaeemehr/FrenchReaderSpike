package com.ziaee.frenchreader.comprehension

import com.ziaee.frenchreader.data.VocabEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComprehensionAnalyzerTest {
    private val lexicon = InMemoryLemmaLexicon(
        forms = mapOf(
            "mangeons" to setOf("manger"),
            "yeux" to setOf("oeil"),
            "arc-en-ciel" to setOf("arc-en-ciel")
        ),
        commonLemmas = setOf("je", "nous", "le", "l'", "d'", "un", "une")
    )

    @Test
    fun tokenizerNormalizesLigaturesAndTypographicApostrophesAndSplitsElisions() {
        val tokens = tokenizeFrench("L’œil, jʼaime l'œuvre.", lexicon)

        assertEquals(listOf("l'", "oeil", "j'", "aime", "l'", "oeuvre"), tokens.map { it.surface })
    }

    @Test
    fun tokenizerKeepsKnownHyphenatedFormsAndSplitsUnknownOnes() {
        val tokens = tokenizeFrench("Un arc-en-ciel bleu-vert.", lexicon)

        assertEquals(listOf("un", "arc-en-ciel", "bleu", "vert"), tokens.map { it.surface })
    }

    @Test
    fun tokenizerDropsDigitsAndPunctuationAndSkipsMarkdownImageLines() {
        val tokens = tokenizeFrench("Bonjour 2026 !\n![chat](images/chat.jpg)\nNous parlons.", lexicon)

        assertEquals(listOf("bonjour", "nous", "parlons"), tokens.map { it.surface })
    }

    @Test
    fun properNounsAreExcludedExceptAtSentenceStart() {
        val tokens = tokenizeFrench("Marie arrive. Je vois Paul. « Luc répond.\"", lexicon)

        assertEquals(listOf("marie", "arrive", "je", "vois", "luc", "répond"), tokens.map { it.surface })
    }

    @Test
    fun lineStartsAndDialogueDashesStartSentences() {
        val tokens = tokenizeFrench("Ailes magiques\n\nUn jour, il part.\n« Oui » Elle rit. – Non, dit Paul.", lexicon)

        assertEquals(
            listOf("ailes", "magiques", "un", "jour", "il", "part", "oui", "elle", "rit", "non", "dit"),
            tokens.map { it.surface }
        )
    }

    @Test
    fun lemmaMatchingRecognizesInflectionsAndNormalizedLigatures() {
        val known = setOf("manger", "oeil")

        assertEquals(100, scoreComprehension(repeatWords("mangeons yeux œil", 7), known, lexicon))
    }

    @Test
    fun knownSetUsesOnlyLearnedOrReviewedBoxTwoEntriesAndSplitsAlternatives() {
        val entries = listOf(
            entry("nouveau", learned = false, box = 1, reviewed = null),
            entry("mangeons", learned = false, box = 2, reviewed = 1L),
            entry("yeux", learned = true, box = 1, reviewed = null),
            entry("un commerçant / une commerçante", learned = true, box = 1, reviewed = null),
            entry("ceci est une phrase beaucoup trop longue", learned = true, box = 1, reviewed = null)
        )

        val known = buildKnownLemmaSet(entries, lexicon)

        assertEquals(
            setOf("je", "nous", "le", "l'", "d'", "un", "une", "manger", "oeil", "commerçant", "commerçante"),
            known
        )
    }

    @Test
    fun scoreReturnsNullBelowTwentyCountedTokens() {
        assertNull(scoreComprehension(repeatWords("mot", 19), setOf("mot"), lexicon))
    }

    @Test
    fun scoreRoundsToWholePercentAtBadgeThresholds() {
        assertEquals(95, scoreComprehension(wordsWithKnownCount(19, 20), setOf("connu"), lexicon))
        assertEquals(85, scoreComprehension(wordsWithKnownCount(17, 20), setOf("connu"), lexicon))
        assertEquals(80, scoreComprehension(wordsWithKnownCount(16, 20), setOf("connu"), lexicon))
    }

    @Test
    fun badgeLevelsUseNinetyFiveAndEightyFivePercentBoundaries() {
        assertEquals(ComprehensionLevel.EASY, comprehensionLevel(95))
        assertEquals(ComprehensionLevel.MANAGEABLE, comprehensionLevel(94))
        assertEquals(ComprehensionLevel.MANAGEABLE, comprehensionLevel(85))
        assertEquals(ComprehensionLevel.HARD, comprehensionLevel(84))
    }

    private fun entry(word: String, learned: Boolean, box: Int, reviewed: Long?) = VocabEntry(
        word = word,
        sentence = "",
        textId = 1,
        dictionaryUrl = "",
        learned = learned,
        leitnerBox = box,
        lastReviewedAtMs = reviewed
    )

    private fun repeatWords(words: String, times: Int): String = List(times) { words }.joinToString(" ")

    private fun wordsWithKnownCount(known: Int, total: Int): String =
        List(known) { "connu" }.plus(List(total - known) { "inconnu" }).joinToString(" ")
}
