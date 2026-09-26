package com.ziaee.frenchreader.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SentenceAlignmentTest {
    private fun align(display: String, sentences: List<String>) =
        alignSentencesToDisplay(display, sanitizeForSpeech(display), sentences)

    @Test
    fun `symbol between sentences goes with the following sentence`() {
        val display = "Bonjour. ✅ Correction ici. Fin 🎉"

        val segments = align(display, listOf("Bonjour.", "Correction ici.", "Fin"))

        assertEquals(listOf("Bonjour.", "✅ Correction ici.", "Fin 🎉"), segments)
        assertEquals(display, segments!!.joinToString(" "))
    }

    @Test
    fun `leading symbol stays in the first sentence`() {
        val segments = align("❌ Elle était. Voilà.", listOf("Elle était.", "Voilà."))

        assertEquals(listOf("❌ Elle était.", "Voilà."), segments)
    }

    @Test
    fun `text without symbols is returned unchanged`() {
        val sentences = listOf("Un.", "Deux.")

        assertEquals(sentences, align("Un. Deux.", sentences))
    }

    @Test
    fun `unmatched sentence gives up`() {
        assertNull(align("A ✅ b.", listOf("autre chose")))
    }

    @Test
    fun `display sanitizer keeps symbols and speech of it matches speech of original`() {
        val raw = "Emoji  🍀­ 👍🏽 ❤️ → fin​"
        val display = sanitizeForDisplay(raw)

        assertEquals("Emoji 🍀 👍🏽 ❤️ → fin", display)
        assertEquals(sanitizeForSpeech(raw), sanitizeForSpeech(display))
    }
}
