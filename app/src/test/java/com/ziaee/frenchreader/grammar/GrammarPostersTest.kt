package com.ziaee.frenchreader.grammar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GrammarPostersTest {
    @Test
    fun `bundled manifest parses`() {
        // Empty until the first posters are produced; it must still be valid.
        parseGrammarPosters(File("src/main/assets/grammar/posters/posters.json").readText())
    }

    @Test
    fun `a poster entry is read and a broken one is skipped`() {
        val posters = parseGrammarPosters(
            """{"version":1,"posters":[
                {"id":"temps-du-passe","titleFr":"Les temps du passé","titleFa":"زمان‌های گذشته","file":"temps-du-passe.pdf","notes":["imparfait"]},
                {"id":"broken"}
            ]}"""
        )
        assertEquals(listOf("temps-du-passe"), posters.map { it.id })
        assertEquals(listOf("imparfait"), posters.single().notes)
    }

    @Test
    fun `malformed manifest gives an empty list`() {
        assertTrue(parseGrammarPosters("not json").isEmpty())
        assertTrue(parseGrammarPosters("""{"version":2,"posters":[]}""").isEmpty())
    }
}
