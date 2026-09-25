package com.ziaee.frenchreader.ui

import com.ziaee.frenchreader.data.VocabEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VocabFlashcardTest {
    @Test
    fun `French word cards speak the word and their sentence`() {
        val entry = entry(word = "bonjour", sentence = "Bonjour à tous.")

        assertEquals("bonjour", flashcardWordAudioText(entry))
        assertEquals("Bonjour à tous.", flashcardSentenceAudioText(entry))
    }

    @Test
    fun `Persian front phrase cards only speak the French sentence`() {
        val entry = entry(word = "خوشحال شدن", sentence = "Il est ravi de vous voir.")

        assertNull(flashcardWordAudioText(entry))
        assertEquals("Il est ravi de vous voir.", flashcardSentenceAudioText(entry))
    }

    private fun entry(word: String, sentence: String) = VocabEntry(
        word = word,
        sentence = sentence,
        textId = 0,
        dictionaryUrl = ""
    )
}
