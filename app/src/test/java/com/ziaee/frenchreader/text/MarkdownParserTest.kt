package com.ziaee.frenchreader.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownParserTest {
    @Test
    fun `keeps emoji on screen but not in spoken text`() {
        val header = MarkdownParser.parse("## 🍀 ACTIVITÉS 🍀")
        assertEquals("🍀 ACTIVITÉS 🍀", header.plainText)
        assertEquals("ACTIVITÉS", header.spokenText)
        assertEquals("QUESTIONS :", MarkdownParser.parse("## 🔷 QUESTIONS :").spokenText)
    }

    @Test
    fun `keeps correction marks in list items`() {
        val block = MarkdownParser.parse("- ❌ **Ta phrase :** dans une soirée")

        assertEquals(BlockType.LIST_ITEM, block.type)
        assertEquals("❌ Ta phrase : dans une soirée", block.plainText)
        assertEquals("Ta phrase : dans une soirée", block.spokenText)
        assertEquals(listOf(EmphasisSpan(2, 13, bold = true, italic = false)), block.emphasisSpans)
    }

    @Test
    fun `recognizes symbol bullet as a list item`() {
        val block = MarkdownParser.parse("• Premier point")

        assertEquals(BlockType.LIST_ITEM, block.type)
        assertEquals("Premier point", block.plainText)
    }

    @Test
    fun `sanitizes visible link text while retaining it`() {
        val block = MarkdownParser.parse("Lire [le 🍀 texte](https://example.com/texte).")

        assertEquals("Lire le 🍀 texte.", block.plainText)
        assertEquals("Lire le texte.", block.spokenText)
    }

    @Test
    fun `emphasis spans use display text offsets`() {
        val block = MarkdownParser.parse("**Bravo** 🎉 *vraiment*")

        assertEquals("Bravo 🎉 vraiment", block.plainText)
        assertEquals("Bravo vraiment", block.spokenText)
        assertEquals(
            listOf(
                EmphasisSpan(0, 5, bold = true, italic = false),
                EmphasisSpan(9, 17, bold = false, italic = true)
            ),
            block.emphasisSpans
        )
    }

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
