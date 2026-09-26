package com.ziaee.frenchreader.ui

import com.ziaee.frenchreader.comprehension.InMemoryLemmaLexicon
import org.junit.Assert.assertEquals
import org.junit.Test

class DictionaryLemmaSuggestionTest {
    private val lexicon = InMemoryLemmaLexicon(
        forms = mapOf(
            "fais" to setOf("faire"),
            "maison" to setOf("maison"),
            "est" to setOf("être", "est", "estre", "étant", "ester")
        ),
        commonLemmas = emptySet()
    )

    @Test
    fun conjugatedFormSuggestsInfinitive() {
        assertEquals(listOf("faire"), lemmaSuggestions("Fais", lexicon))
    }

    @Test
    fun wordThatIsItsOwnLemmaSuggestsNothing() {
        assertEquals(emptyList<String>(), lemmaSuggestions("maison", lexicon))
    }

    @Test
    fun suggestionsAreCappedAtThreeAndSkipTheWordItself() {
        assertEquals(listOf("être", "estre", "étant"), lemmaSuggestions("est", lexicon))
    }

    @Test
    fun unknownWordOrPhraseSuggestsNothing() {
        assertEquals(emptyList<String>(), lemmaSuggestions("zzz", lexicon))
        assertEquals(emptyList<String>(), lemmaSuggestions("faire attention", lexicon))
    }
}
