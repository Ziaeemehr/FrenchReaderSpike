package com.ziaee.frenchreader.grammar

import android.content.res.AssetManager
import org.json.JSONArray
import org.json.JSONObject

data class HighlightRange(val start: Int, val end: Int)
data class GrammarCell(val text: String, val highlights: List<HighlightRange> = emptyList())
data class GrammarParagraph(val kind: String, val text: String)
data class GrammarExample(val id: String, val fr: GrammarCell, val fa: String)
sealed interface GrammarBlock {
    data class Prose(val type: String, val markdown: List<GrammarParagraph>) : GrammarBlock
    data class Structure(val patterns: List<String>, val text: List<GrammarParagraph>) : GrammarBlock
    data class Examples(val items: List<GrammarExample>) : GrammarBlock
    data class Mistake(val wrong: String, val right: String, val why: String) : GrammarBlock
    data class Infographic(val kind: String, val header: List<GrammarCell>, val rows: List<List<GrammarCell>>) : GrammarBlock
}
data class GrammarNote(
    val id: String, val group: String, val level: String,
    val titleFr: String, val titleFa: String,
    val aliasesFr: List<String>, val aliasesFa: List<String>, val related: List<String>,
    val blocks: List<GrammarBlock>
)

fun loadGrammarNotes(assets: AssetManager): List<GrammarNote> =
    assets.open("grammar/notes.json").bufferedReader().use { parseGrammarNotes(it.readText()) }

fun parseGrammarNotes(json: String): List<GrammarNote> {
    val root = JSONObject(json)
    require(root.getInt("version") == 1)
    return root.getJSONArray("notes").map { index ->
        runCatching { parseNote(root.getJSONArray("notes").getJSONObject(index)) }.getOrNull()
    }.filterNotNull()
}

private fun parseNote(note: JSONObject): GrammarNote {
    val group = note.getString("group")
    require(group in setOf("basics", "tenses", "pronouns", "structures", "comparisons"))
    val blocks = note.getJSONArray("blocks").map { index ->
        val block = note.getJSONArray("blocks").getJSONObject(index)
        when (val type = block.getString("type")) {
            "summary", "usage", "more" -> GrammarBlock.Prose(type, block.getJSONArray("markdown").paragraphs())
            "structure" -> GrammarBlock.Structure(block.getJSONArray("patterns").strings(), block.getJSONArray("text").paragraphs())
            "examples" -> GrammarBlock.Examples(block.getJSONArray("items").map { i ->
                val example = block.getJSONArray("items").getJSONObject(i)
                GrammarExample(example.getString("id"), example.cell("fr"), example.getString("fa"))
            })
            "mistake" -> GrammarBlock.Mistake(block.getString("wrong"), block.getString("right"), block.getString("why"))
            "infographic" -> {
                val header = block.getJSONArray("header").cells()
                val rows = block.getJSONArray("rows").map { i -> block.getJSONArray("rows").getJSONArray(i).cells() }
                require(header.isNotEmpty() && rows.all { it.size == header.size })
                GrammarBlock.Infographic(block.getString("kind"), header, rows)
            }
            else -> error("Unknown block: $type")
        }
    }
    require(listOf("summary", "usage").all { type -> blocks.any { it is GrammarBlock.Prose && it.type == type } })
    require(blocks.any { it is GrammarBlock.Structure } && blocks.any { it is GrammarBlock.Examples })
    return GrammarNote(note.getString("id"), group, note.getString("level"), note.getString("titleFr"),
        note.getString("titleFa"), note.getJSONArray("aliasesFr").strings(), note.getJSONArray("aliasesFa").strings(),
        note.getJSONArray("related").strings(), blocks)
}

private fun JSONObject.cell(key: String = "text"): GrammarCell {
    val text = getString(key)
    val ranges = getJSONArray("highlights").map { i ->
        val range = getJSONArray("highlights").getJSONArray(i)
        require(range.length() == 2)
        HighlightRange(range.getInt(0), range.getInt(1)).also {
            require(it.start >= 0 && it.end > it.start && it.end <= text.length)
        }
    }
    return GrammarCell(text, ranges)
}
private fun JSONArray.cells() = map { getJSONObject(it).cell() }
private fun JSONArray.strings() = map { getString(it) }
private fun JSONArray.paragraphs() = map {
    val paragraph = getJSONObject(it)
    val kind = paragraph.getString("kind")
    require(kind == "p" || kind == "li")
    GrammarParagraph(kind, paragraph.getString("text"))
}
private fun <T> JSONArray.map(transform: (Int) -> T): List<T> = (0 until length()).map(transform)
