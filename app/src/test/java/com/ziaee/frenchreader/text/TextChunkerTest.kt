package com.ziaee.frenchreader.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextChunkerTest {
    @Test
    fun `removes bare URLs and drops source-only lines`() {
        val chunks = TextChunker.chunk(
            "Source: https://www.podcastfrancaisfacile.com/texte/14-juillet-texte-fle.html\n\n" +
                "Voir www.example.com pour plus.\n\nLien : https://example.com"
        )

        assertEquals(listOf("Voir pour plus."), chunks)
    }

    @Test
    fun `keeps Markdown link destinations for the parser`() {
        val chunks = TextChunker.chunk("Lire [le texte](https://example.com/texte).")

        assertEquals(listOf("Lire [le texte](https://example.com/texte)."), chunks)
        assertEquals("Lire le texte.", MarkdownParser.parse(chunks.single()).plainText)
    }

    @Test
    fun `drops separators and blocks empty after speech sanitizing`() {
        val chunks = TextChunker.chunk("---\n\n* * *\n\n═══\n\n🍀👏\n\nTexte")

        assertEquals(listOf("Texte"), chunks)
    }

    @Test
    fun `normalizes supported symbol bullets into list chunks`() {
        val bullets = listOf("•", "▪", "◦", "‣", "●", "■", "►", "▶", "✓", "✔", "➤", "→")
        val chunks = TextChunker.chunk(bullets.joinToString("\n") { "$it Point" })

        assertEquals(bullets.map { "- Point" }, chunks)
        assertTrue(chunks.all { MarkdownParser.parse(it).type == BlockType.LIST_ITEM })
    }

    @Test
    fun `full French document produces only speakable deterministic blocks`() {
        val chunks = TextChunker.chunk(
            """
            # Le 14 juillet
            ## 🍀 ACTIVITÉS 🍀
            • Écoutez le texte.
            • **Bravo** 🎉 *vraiment*

            ## 🔷 QUESTIONS :
            Voir www.example.com pour plus.

            Source: https://www.podcastfrancaisfacile.com/texte/14-juillet-texte-fle.html
            ---
            """.trimIndent()
        )

        assertEquals(
            listOf(
                "Le 14 juillet",
                "ACTIVITÉS",
                "Écoutez le texte.",
                "Bravo vraiment",
                "QUESTIONS :",
                "Voir pour plus."
            ),
            chunks.map { MarkdownParser.parse(it).spokenText }
        )
    }

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
