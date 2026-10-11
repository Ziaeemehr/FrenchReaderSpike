package com.ziaee.frenchreader.grammar

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrammarMarkdownTest {
    private val labels = GrammarExportLabels(
        "کاربرد", "ساختار", "مثال‌ها", "اشتباه رایج", "نکته‌های بیشتر", "مقایسه",
        "خط زمانی", "انتخاب ساختار", "مراحل", "درست", "نادرست", "Test App"
    )

    @Test fun `bundled comparison notes export sections highlights and complete tables`() {
        val notes = parseGrammarNotes(File("src/main/assets/grammar/notes.json").readText())
        listOf("passe-compose-vs-imparfait", "qui-vs-que").forEach { id ->
            val note = notes.first { it.id == id }
            val markdown = grammarNoteToMarkdown(note, labels)
            assertTrue(markdown.startsWith("# ${note.titleFr}\n"))
            assertTrue(markdown.contains("\n${note.titleFa}\n"))
            assertTrue(markdown.contains("\n${note.level}\n"))
            val titles = note.blocks.mapNotNull { block ->
                when (block) {
                    is GrammarBlock.Prose -> when (block.type) { "summary" -> null; "usage" -> labels.usage; else -> labels.more }
                    is GrammarBlock.Structure -> labels.structure
                    is GrammarBlock.Examples -> labels.examples
                    is GrammarBlock.Mistake -> labels.mistake
                    is GrammarBlock.Infographic -> when (block.kind) {
                        "timeline" -> labels.timeline; "decision" -> labels.decision; "steps" -> labels.steps; else -> labels.comparison
                    }
                }
            }
            titles.forEach { title -> assertEquals(1, markdown.lines().count { it == "## $title" }) }
            val examples = note.blocks.filterIsInstance<GrammarBlock.Examples>().flatMap { it.items }
            examples.forEach { example ->
                assertTrue(markdown.contains(markdownGrammarCell(example.fr)))
                example.fr.highlights.forEach { range ->
                    assertTrue(markdown.contains("**${example.fr.text.substring(range.start, range.end)}**"))
                }
            }
            if (id == "passe-compose-vs-imparfait") {
                assertTrue(markdown.contains("Je **regardais** la télé quand il **est arrivé**."))
            } else assertTrue(markdown.contains("**qui**"))
            val table = note.blocks.filterIsInstance<GrammarBlock.Infographic>().single()
            val rows = markdown.lines().filter { it.startsWith("| ") }
            assertEquals(table.rows.size + 2, rows.size)
            assertEquals("| " + List(table.header.size) { "---" }.joinToString(" | ") + " |", rows[1])
            rows.forEach { assertEquals(table.header.size + 2, it.split('|').size) }
            assertFalse(markdown.any { it in '\u2066'..'\u2069' || it == '\u00A0' })
            assertTrue(markdown.endsWith(
                "---\n\nTest App\n\nGitHub: https://github.com/Ziaeemehr/FrenchReaderSpike\n\n" +
                    "Bazaar: https://cafebazaar.ir/app/com.ziaee.frenchreader\n"
            ))
            val pdf = grammarNoteToPdfBlocks(note, labels)
            assertEquals(com.ziaee.frenchreader.share.PdfBlock.Heading(note.titleFr, 1), pdf.first())
            assertEquals(titles, pdf.filterIsInstance<com.ziaee.frenchreader.share.PdfBlock.Heading>().drop(1).map { it.text })
        }
    }

    @Test fun `adjacent overlapping and unordered highlights merge`() {
        assertEquals("a**bcde**f", markdownGrammarCell(GrammarCell("abcdef", listOf(
            HighlightRange(3, 5), HighlightRange(1, 3), HighlightRange(2, 4)
        ))))
    }

    @Test fun `table pipes escape and display controls are removed`() {
        val note = parseGrammarNotes(File("src/main/assets/grammar/notes.json").readText()).first()
        val table = GrammarBlock.Infographic("comparison", listOf(GrammarCell("a|b")), listOf(listOf(GrammarCell("x\u00A0\u2066y\u2069"))))
        val markdown = grammarNoteToMarkdown(note.copy(blocks = listOf(table)), labels)
        assertTrue(markdown.contains("| a\\|b |\n| --- |\n| x y |"))
    }
}
