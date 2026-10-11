package com.ziaee.frenchreader.grammar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrammarHistoryTest {
    @Test fun `recents sort dedupe and cap without discarding unknown ids`() {
        val source = (0..25).map { GrammarRecent("unknown-$it", it.toLong()) } + GrammarRecent("unknown-25", 1)
        val recents = normalizeGrammarRecents(source.reversed())
        assertEquals(20, recents.size)
        assertEquals((25 downTo 6).map { "unknown-$it" }, recents.map { it.id })
        val opened = openGrammarRecent(recents, "unknown-10", 30)
        assertEquals(GrammarRecent("unknown-10", 30), opened.first())
        assertEquals(20, opened.size)
        assertEquals(1, opened.count { it.id == "unknown-10" })
        assertEquals(20, openGrammarRecent(opened, "new", 31).size)
    }

    @Test fun `favourites toggle preserves other ids`() {
        val added = toggleGrammarFavourite(setOf("unknown"), "note")
        assertEquals(setOf("unknown", "note"), added)
        assertEquals(setOf("unknown"), toggleGrammarFavourite(added, "note"))
    }

    @Test fun `storage round trips escaped ids empty and corrupted values`() {
        val state = GrammarPrefsState(setOf("unknown", "id\t%\nفارسی"), listOf(GrammarRecent("missing", 123)))
        assertEquals(state, decodeGrammarPrefs(encodeGrammarPrefs(state)))
        assertEquals(GrammarPrefsState(), decodeGrammarPrefs(encodeGrammarPrefs(GrammarPrefsState())))
        listOf("", "broken", "v1\nr\tnot-a-time\tid\n", "v1\nf\t%bad\n", "v1\nf\n").forEach {
            assertEquals(GrammarPrefsState(), decodeGrammarPrefs(it))
        }
        assertTrue(decodeGrammarPrefs("v1\nfuture\tvalue\nf\tnote\n").favourites.contains("note"))
    }
}
