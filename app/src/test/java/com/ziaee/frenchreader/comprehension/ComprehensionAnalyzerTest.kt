package com.ziaee.frenchreader.comprehension

import org.junit.Assert.assertEquals
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
        val tokens = tokenizeFrench("mangeons yeux œil", lexicon)
        assertEquals(listOf(setOf("manger"), setOf("oeil"), setOf("oeil")), tokens.map { it.lemmas })
    }
}
