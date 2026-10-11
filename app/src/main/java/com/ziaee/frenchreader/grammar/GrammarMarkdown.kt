package com.ziaee.frenchreader.grammar

data class GrammarExportLabels(
    val usage: String, val structure: String, val examples: String, val mistake: String,
    val more: String, val comparison: String, val timeline: String, val decision: String,
    val steps: String, val correct: String, val wrong: String, val appName: String
)

fun grammarNoteToMarkdown(note: GrammarNote, labels: GrammarExportLabels): String {
    fun prose(paragraphs: List<GrammarParagraph>) = paragraphs.joinToString("\n\n") {
        (if (it.kind == "li") "- " else "") + it.text
    }
    fun tableRow(cells: List<GrammarCell>) = cells.joinToString(" | ", "| ", " |") {
        markdownGrammarCell(it).replace("|", "\\|").replace("\n", "<br>")
    }
    val output = buildString {
        append("# ${note.titleFr}\n\n${note.titleFa}\n\n${note.level}\n\n")
        note.blocks.forEach { block ->
            val title = when (block) {
                is GrammarBlock.Prose -> when (block.type) { "summary" -> null; "usage" -> labels.usage; else -> labels.more }
                is GrammarBlock.Structure -> labels.structure
                is GrammarBlock.Examples -> labels.examples
                is GrammarBlock.Mistake -> labels.mistake
                is GrammarBlock.Infographic -> when (block.kind) {
                    "timeline" -> labels.timeline; "decision" -> labels.decision; "steps" -> labels.steps; else -> labels.comparison
                }
            }
            if (title != null) append("## $title\n\n")
            when (block) {
                is GrammarBlock.Prose -> {
                    val text = prose(block.markdown)
                    append(if (block.type == "summary") text.lines().joinToString("\n") { "> $it" } else text)
                }
                is GrammarBlock.Structure -> {
                    append(block.patterns.joinToString("\n\n") { "`$it`" })
                    append("\n\n${prose(block.text)}")
                }
                is GrammarBlock.Examples -> append(block.items.mapIndexed { index, example ->
                    "${index + 1}. ${markdownGrammarCell(example.fr)}\n" + example.fa.lines().joinToString("\n") { "   $it" }
                }.joinToString("\n\n"))
                is GrammarBlock.Mistake -> append("- ✗ ${labels.wrong}: ${block.wrong}\n- ✓ ${labels.correct}: ${block.right}\n\n${block.why}")
                is GrammarBlock.Infographic -> {
                    append(tableRow(block.header))
                    append("\n" + block.header.joinToString(" | ", "| ", " |") { "---" })
                    block.rows.forEach { append("\n${tableRow(it)}") }
                }
            }
            append("\n\n")
        }
        append("---\n\n${labels.appName}\n")
    }
    return output.filterNot { it in '\u2066'..'\u2069' }.replace('\u00A0', ' ')
}

internal fun markdownGrammarCell(cell: GrammarCell): String {
    val ranges = mutableListOf<HighlightRange>()
    cell.highlights.sortedBy { it.start }.forEach { range ->
        val last = ranges.lastOrNull()
        if (last != null && range.start <= last.end) {
            ranges[ranges.lastIndex] = HighlightRange(last.start, maxOf(last.end, range.end))
        } else ranges.add(range)
    }
    return buildString {
        var position = 0
        ranges.forEach {
            append(cell.text.substring(position, it.start))
            append("**${cell.text.substring(it.start, it.end)}**")
            position = it.end
        }
        append(cell.text.substring(position))
    }
}
