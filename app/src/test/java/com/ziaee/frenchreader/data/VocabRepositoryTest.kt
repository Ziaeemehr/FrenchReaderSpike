package com.ziaee.frenchreader.data

import org.junit.Assert.assertEquals
import org.junit.Test

class VocabRepositoryTest {
    @Test
    fun vocabIdentityKeyIsUnicodeNormalizedAndCaseInsensitive() {
        assertEquals(vocabIdentityKey("École"), vocabIdentityKey("école"))
        assertEquals(vocabIdentityKey("école"), vocabIdentityKey("e\u0301cole"))
    }

    @Test
    fun dictionarySavedStateDistinguishesExactEntryFromOtherContexts() {
        val exact = entry(id = 1, textId = 10, word = "École", sentence = "Cette école est grande.")
        val other = entry(id = 2, textId = 20, word = "e\u0301cole", sentence = "Une autre école.")

        val result = dictionarySavedState(
            entries = listOf(exact, other),
            textId = 10,
            word = "école",
            sentence = "Cette école est grande."
        )

        assertEquals(exact, result.exactEntry)
        assertEquals(2, result.totalWordMatches)
        assertEquals(1, result.otherContextCount)
    }

    @Test
    fun dictionarySavedStateReportsExistingWordWithoutMarkingContextSaved() {
        val elsewhere = entry(id = 3, textId = 20, word = "bonjour", sentence = "Bonjour Marie.")

        val result = dictionarySavedState(
            entries = listOf(elsewhere),
            textId = 10,
            word = "Bonjour",
            sentence = "Bonjour Paul."
        )

        assertEquals(null, result.exactEntry)
        assertEquals(1, result.totalWordMatches)
        assertEquals(1, result.otherContextCount)
    }

    private fun entry(id: Long, textId: Long, word: String, sentence: String) = VocabEntry(
        id = id,
        textId = textId,
        word = word,
        sentence = sentence,
        dictionaryUrl = "dictionary",
        meaning = "meaning"
    )
}
