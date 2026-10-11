package com.ziaee.frenchreader.exams

import android.content.res.AssetManager
import com.ziaee.frenchreader.share.PdfBlock
import com.ziaee.frenchreader.share.PdfText
import org.json.JSONArray
import org.json.JSONObject

data class ExamSource(val id: String, val title: String, val url: String)

/** [sources] are ids into [Exam.sources]: every statement says where it comes from. */
data class ExamParagraph(val kind: String, val text: String, val sources: List<String>)

sealed interface ExamSection {
    val titleFa: String
    data class Text(override val titleFa: String, val paragraphs: List<ExamParagraph>) : ExamSection
    data class Table(
        override val titleFa: String, val header: List<String>, val rows: List<List<String>>, val sources: List<String>
    ) : ExamSection
}

/**
 * One exam (TCF today; others are added as further entries of assets/exams/exams.json).
 * Explanations are in Persian, like the grammar notes; [checkedOn] is the day the figures were
 * last compared with the sources.
 */
data class Exam(
    val id: String, val titleFr: String, val titleFa: String, val summaryFa: String, val checkedOn: String,
    val sections: List<ExamSection>, val questionsFile: String?, val sources: List<ExamSource>
) {
    /** 1-based reference numbers, in the order of [sources]; unknown ids are dropped. */
    fun sourceNumbers(ids: List<String>): List<Int> =
        ids.map { id -> sources.indexOfFirst { it.id == id } + 1 }.filter { it > 0 }.distinct().sorted()
}

fun loadExams(assets: AssetManager): List<Exam> =
    assets.open("exams/exams.json").bufferedReader().use { parseExams(it.readText()) }

/** An exam that does not parse is skipped, so one bad entry cannot hide the others. */
fun parseExams(json: String): List<Exam> {
    val root = JSONObject(json)
    require(root.getInt("version") == 1)
    return root.getJSONArray("exams").objects().mapNotNull { runCatching { parseExam(it) }.getOrNull() }
}

private fun parseExam(exam: JSONObject): Exam {
    val sources = exam.getJSONArray("sources").objects().map {
        ExamSource(it.getString("id"), it.getString("title"), it.getString("url"))
    }
    val known = sources.map { it.id }.toSet()
    fun JSONObject.sourceIds(): List<String> =
        (optJSONArray("sources")?.strings() ?: emptyList()).also { ids -> require(ids.all { it in known }) }
    val sections = exam.getJSONArray("sections").objects().map { section ->
        val title = section.getString("titleFa")
        when (val type = section.getString("type")) {
            "text" -> ExamSection.Text(title, section.getJSONArray("paragraphs").objects().map {
                val kind = it.getString("kind")
                require(kind == "p" || kind == "li")
                ExamParagraph(kind, it.getString("text"), it.sourceIds())
            })
            "table" -> {
                val header = section.getJSONArray("header").strings()
                val rows = section.getJSONArray("rows").let { rows -> (0 until rows.length()).map { rows.getJSONArray(it).strings() } }
                require(header.isNotEmpty() && rows.all { it.size == header.size })
                ExamSection.Table(title, header, rows, section.sourceIds())
            }
            else -> error("Unknown section: $type")
        }
    }
    return Exam(
        exam.getString("id"), exam.getString("titleFr"), exam.getString("titleFa"), exam.getString("summaryFa"),
        exam.getString("checkedOn"), sections, exam.optString("questionsFile").takeIf(String::isNotBlank), sources
    )
}

private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
private fun JSONArray.strings() = (0 until length()).map { getString(it) }

/**
 * " [1–4, 6]": runs of three or more sources are collapsed. With [isolate] the label is wrapped in a
 * left-to-right isolate, so "1–4" does not turn into "4–1" at the end of a Persian line.
 */
fun referenceLabel(numbers: List<Int>, isolate: Boolean = false): String {
    if (numbers.isEmpty()) return ""
    val parts = mutableListOf<String>()
    var start = 0
    while (start < numbers.size) {
        var end = start
        while (end + 1 < numbers.size && numbers[end + 1] == numbers[end] + 1) end++
        if (end - start >= 2) parts.add("${numbers[start]}–${numbers[end]}") else (start..end).forEach { parts.add(numbers[it].toString()) }
        start = end + 1
    }
    val label = parts.joinToString(prefix = "[", postfix = "]")
    return " " + if (isolate) "\u2066$label\u2069" else label
}

/** The rules and formats of [exam] as plain Markdown, ending with its sources. The caller adds the signature. */
fun examToMarkdown(exam: Exam, sourcesLabel: String): String = buildString {
    append("# ${exam.titleFr}\n\n${exam.titleFa}\n\n> ${exam.summaryFa}\n\n")
    exam.sections.forEach { section ->
        append("## ${section.titleFa}${if (section is ExamSection.Table) referenceLabel(exam.sourceNumbers(section.sources)) else ""}\n\n")
        when (section) {
            is ExamSection.Text -> append(section.paragraphs.joinToString(if (section.paragraphs.all { it.kind == "li" }) "\n" else "\n\n") {
                (if (it.kind == "li") "- " else "") + it.text + referenceLabel(exam.sourceNumbers(it.sources))
            })
            is ExamSection.Table -> {
                fun row(cells: List<String>) = cells.joinToString(" | ", "| ", " |") { it.replace("|", "\\|") }
                append(row(section.header)).append("\n").append(row(section.header.map { "---" }))
                section.rows.forEach { append("\n").append(row(it)) }
            }
        }
        append("\n\n")
    }
    append("## $sourcesLabel (${exam.checkedOn})\n\n")
    exam.sources.forEachIndexed { index, source -> append("${index + 1}. ${source.title}: ${source.url}\n") }
}

fun examToPdfBlocks(exam: Exam, sourcesLabel: String): List<PdfBlock> = buildList {
    add(PdfBlock.Heading(exam.titleFr, 1))
    add(PdfBlock.Paragraph(PdfText(exam.titleFa, rtl = true)))
    add(PdfBlock.Paragraph(PdfText(exam.summaryFa), quote = true))
    exam.sections.forEach { section ->
        when (section) {
            is ExamSection.Text -> {
                add(PdfBlock.Heading(section.titleFa, 2))
                section.paragraphs.forEach {
                    add(PdfBlock.Paragraph(PdfText((if (it.kind == "li") "• " else "") + it.text + referenceLabel(exam.sourceNumbers(it.sources), isolate = true))))
                }
            }
            is ExamSection.Table -> {
                add(PdfBlock.Heading(section.titleFa + referenceLabel(exam.sourceNumbers(section.sources), isolate = true), 2))
                add(PdfBlock.Table(section.header.map { PdfText(it) }, section.rows.map { row -> row.map { PdfText(it) } }))
            }
        }
    }
    add(PdfBlock.Heading("$sourcesLabel (${exam.checkedOn})", 2))
    exam.sources.forEachIndexed { index, source -> add(PdfBlock.Paragraph(PdfText("${index + 1}. ${source.title}\n${source.url}", rtl = false))) }
}
