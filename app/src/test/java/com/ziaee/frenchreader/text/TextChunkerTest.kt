package com.ziaee.frenchreader.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextChunkerTest {
    @Test
    fun `epub image paragraph is isolated without surrounding blank lines`() {
        val chunks = TextChunker.chunk(
            "Avant.\n![Carte](epubimg:abc123/4_2.jpg)\nAprès."
        )

        assertEquals(
            listOf("Avant.", "![Carte](epubimg:abc123/4_2.jpg)", "Après."),
            chunks
        )
    }

    @Test
    fun `long paragraph without sentence punctuation is still bounded`() {
        val chunks = TextChunker.chunk("mot ".repeat(1_000).trim())

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 1_200 })
    }
}
