package com.ziaee.frenchreader.text

import org.junit.Assert.assertEquals
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
}
