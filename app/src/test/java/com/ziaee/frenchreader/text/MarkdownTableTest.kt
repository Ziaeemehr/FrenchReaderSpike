package com.ziaee.frenchreader.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTableTest {
    private val table = """
        | بخش | توضیح |
        |-----|-------|
        | ۱. سلام | شروع **دوستانه** |
        | ۲. پاسخ | a \| b |
    """.trimIndent()

    @Test
    fun `pipe table parses into rows of cells`() {
        val block = MarkdownParser.parse(table)
        assertEquals(BlockType.TABLE, block.type)
        val cells = block.tableRows.map { row -> row.map { block.plainText.substring(it.start, it.end) } }
        assertEquals(
            listOf(listOf("بخش", "توضیح"), listOf("۱. سلام", "شروع دوستانه"), listOf("۲. پاسخ", "a | b")),
            cells
        )
        val bold = block.emphasisSpans.single()
        assertEquals("دوستانه", block.plainText.substring(bold.start, bold.end))
        assertEquals("بخش, توضیح. ۱. سلام, شروع دوستانه. ۲. پاسخ, a | b.", block.spokenText)
    }

    @Test
    fun `short rows are padded to the header width`() {
        val block = MarkdownParser.parse("| a | b | c |\n|---|:-:|--:|\n| x |")
        assertEquals(listOf(3, 3), block.tableRows.map { it.size })
    }

    @Test
    fun `chunker keeps a table as one block between paragraphs`() {
        val chunks = TextChunker.chunk("Avant le tableau.\n$table\nAprès le tableau.")
        assertEquals(3, chunks.size)
        assertTrue(MarkdownParser.isTable(chunks[1]))
        assertEquals("Après le tableau.", chunks[2])
    }

    @Test
    fun `pipe lines without a delimiter row stay paragraph text`() {
        val chunks = TextChunker.chunk("| pas un tableau |\n| vraiment |")
        assertEquals(1, chunks.size)
        assertFalse(MarkdownParser.isTable(chunks[0]))
        assertEquals(BlockType.PARAGRAPH, MarkdownParser.parse(chunks[0]).type)
    }
}
