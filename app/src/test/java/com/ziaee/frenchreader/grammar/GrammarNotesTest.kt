package com.ziaee.frenchreader.grammar

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GrammarNotesTest {
    private fun asset() = File("src/main/assets/grammar/notes.json").readText()

    // The bundled note count grows with the content, so tests compare against the asset itself.
    private val total = JSONObject(asset()).getJSONArray("notes").length()

    @Test
    fun `generated notes parse with ordered blocks and UTF16 highlights`() {
        val notes = parseGrammarNotes(asset())
        assertEquals(total, notes.size)
        val note = notes.first { it.id == "bien-que" }
        assertTrue(note.blocks.first() is GrammarBlock.Prose)
        val example = (note.blocks.first { it is GrammarBlock.Examples } as GrammarBlock.Examples).items.first()
        assertEquals("Bien qu'", example.fr.text.substring(example.fr.highlights.first().start, example.fr.highlights.first().end))
        assertTrue(notes.flatMap { it.blocks }.any { it is GrammarBlock.Infographic })
    }

    @Test
    fun `malformed note is skipped while valid neighbors survive`() {
        val root = JSONObject(asset())
        val notes = root.getJSONArray("notes")
        notes.put(JSONObject().put("id", "broken"))
        assertEquals(total, parseGrammarNotes(root.toString()).size)
        notes.getJSONObject(0).getJSONArray("blocks").getJSONObject(0).put("type", "unknown")
        assertEquals(total - 1, parseGrammarNotes(root.toString()).size)
    }

    @Test
    fun `invalid highlight range and table shape skip only their note`() {
        val root = JSONObject(asset())
        val notes = root.getJSONArray("notes")
        val target = notes.getJSONObject(0)
        val blocks = target.getJSONArray("blocks")
        val examples = (0 until blocks.length()).map { blocks.getJSONObject(it) }.first { it.getString("type") == "examples" }
        examples.getJSONArray("items").getJSONObject(0).put("highlights", org.json.JSONArray("[[-1,999]]"))
        assertEquals(total - 1, parseGrammarNotes(root.toString()).size)
        val tableRoot = JSONObject(asset())
        val tableNotes = tableRoot.getJSONArray("notes")
        // Not every note has an infographic; take the first block of that type in any note.
        val table = (0 until tableNotes.length()).flatMap { index ->
            val tableBlocks = tableNotes.getJSONObject(index).getJSONArray("blocks")
            (0 until tableBlocks.length()).map { tableBlocks.getJSONObject(it) }
        }.first { it.getString("type") == "infographic" }
        table.getJSONArray("rows").put(org.json.JSONArray())
        assertEquals(total - 1, parseGrammarNotes(tableRoot.toString()).size)
    }
}
