package com.ziaee.frenchreader.ui

import com.ziaee.frenchreader.data.VocabEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardShadowingTest {
    @Test
    fun `shadow text prefers French sentence then French word`() {
        assertEquals("Il est ravi.", flashcardShadowText(entry("خوشحال", "Il est ravi.")))
        assertEquals("bonjour", flashcardShadowText(entry("bonjour", "")))
        assertEquals("bonjour", flashcardShadowText(entry("bonjour", "سلام")))
        assertNull(flashcardShadowText(entry("سلام", "")))
        assertNull(flashcardShadowText(entry("سلام", "سلام")))
    }

    @Test
    fun `session accuracy weights words across all scored attempts`() {
        val stats = CardPronunciationStats()
        assertNull(stats.accuracy)
        assertEquals(50, stats.after(1, 1).after(1, 3).accuracy)
        assertEquals(0, stats.after(0, 3).accuracy)
        assertEquals(67, stats.after(2, 3).accuracy)
    }

    private fun entry(word: String, sentence: String) = VocabEntry(
        word = word, sentence = sentence, textId = 0, dictionaryUrl = ""
    )
}
