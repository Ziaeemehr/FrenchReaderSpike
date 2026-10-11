package com.ziaee.frenchreader.exams

import com.ziaee.frenchreader.share.PdfBlock
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamContentTest {
    private val exams = parseExams(File("src/main/assets/exams/exams.json").readText())

    @Test fun `bundled TCF guide parses and every statement cites a known source`() {
        // A dropped exam means a broken table or an unknown source id.
        assertEquals(listOf("tcf"), exams.map { it.id })
        val tcf = exams.single()
        assertEquals("tcf-questions.json", tcf.questionsFile)
        assertTrue(tcf.sources.all { it.url.startsWith("https://www.france-education-international.fr/") })
        tcf.sections.forEach { section ->
            when (section) {
                is ExamSection.Text -> section.paragraphs.forEach { assertTrue(it.text, it.sources.isNotEmpty()) }
                is ExamSection.Table -> assertTrue(section.titleFa, section.sources.isNotEmpty())
            }
        }
        val used = tcf.sections.flatMap { section ->
            when (section) {
                is ExamSection.Text -> section.paragraphs.flatMap { it.sources }
                is ExamSection.Table -> section.sources
            }
        }.toSet()
        assertEquals(tcf.sources.map { it.id }.toSet(), used)
    }

    @Test fun `an exam with an unknown source or a ragged table is skipped`() {
        fun exam(section: String) = """{"version":1,"exams":[{"id":"x","titleFr":"X","titleFa":"X","summaryFa":"s",
            "checkedOn":"2026-01-01","sections":[$section],"sources":[{"id":"a","title":"A","url":"https://a"}]}]}"""
        assertEquals(1, parseExams(exam("""{"type":"table","titleFa":"t","header":["a","b"],"rows":[["1","2"]],"sources":["a"]}""")).size)
        assertEquals(0, parseExams(exam("""{"type":"table","titleFa":"t","header":["a","b"],"rows":[["1"]],"sources":["a"]}""")).size)
        assertEquals(0, parseExams(exam("""{"type":"text","titleFa":"t","paragraphs":[{"kind":"p","text":"x","sources":["b"]}]}""")).size)
    }

    @Test fun `exports number the sources and keep every table`() {
        val tcf = exams.single()
        assertEquals(listOf(1, 3), tcf.sourceNumbers(listOf("quebec", "tp", "tp", "missing")))
        assertEquals(" [1–4, 6]", referenceLabel(listOf(1, 2, 3, 4, 6)))
        assertEquals(" [1, 2, 5]", referenceLabel(listOf(1, 2, 5)))
        assertEquals("", referenceLabel(emptyList(), isolate = true))
        val markdown = examToMarkdown(tcf, "Sources")
        assertTrue(markdown.none { it in '\u2066'..'\u2069' })
        tcf.sources.forEachIndexed { index, source -> assertTrue(markdown.contains("${index + 1}. ${source.title}: ${source.url}")) }
        val tables = tcf.sections.filterIsInstance<ExamSection.Table>()
        assertEquals(tables.sumOf { it.rows.size + 2 }, markdown.lines().count { it.startsWith("| ") })
        val pdf = examToPdfBlocks(tcf, "Sources")
        assertEquals(tables.map { it.rows }, pdf.filterIsInstance<PdfBlock.Table>().map { table -> table.rows.map { row -> row.map { it.text } } })
    }
}
