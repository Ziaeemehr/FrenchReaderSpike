package com.ziaee.frenchreader.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownParserTest {
    @Test
    fun `standalone epub image marker becomes an image block`() {
        val block = MarkdownParser.parse("![Une carte](epubimg:abc123/4_2.jpg)")

        assertEquals(BlockType.IMAGE, block.type)
        assertEquals("", block.plainText)
        assertEquals(emptyList<EmphasisSpan>(), block.emphasisSpans)
        assertEquals("abc123/4_2.jpg", block.imageRef)
        assertEquals("Une carte", block.imageAlt)
    }

    @Test
    fun `standalone web image is still dropped from a paragraph`() {
        val block = MarkdownParser.parse("![Une carte](https://example.com/map.jpg)")

        assertEquals(BlockType.PARAGRAPH, block.type)
        assertEquals("", block.plainText)
        assertNull(block.imageRef)
    }

    @Test
    fun `epub image marker inline in a sentence is still dropped`() {
        val block = MarkdownParser.parse("Avant ![carte](epubimg:abc123/4_2.jpg) après.")

        assertEquals(BlockType.PARAGRAPH, block.type)
        assertEquals("Avant  après.", block.plainText)
        assertNull(block.imageRef)
    }
}
