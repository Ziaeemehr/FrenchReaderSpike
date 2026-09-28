package com.ziaee.frenchreader.text

import org.junit.Assert.assertFalse
import org.junit.Test

class TextChunkerGermanTest {
    @Test fun `never splits a chunk between a german ordinal and its noun`() {
        // The ordinal ends a piece that fills most of a chunk, so the next capitalised noun
        // would start a new chunk if "25." counted as a sentence end.
        val filler = "Viele Menschen in Serbien warten gespannt auf die Wahl"
        val head = generateSequence { filler }.take(20).joinToString(" und ") // ~1175 of the 1200-char chunk limit
        val text = "$head am 25. Oktober beginnt dann endlich die lange erwartete Abstimmung im ganzen Land."
        val chunks = TextChunker.chunk(text)
        assertFalse(chunks.toString(), chunks.any { it.endsWith("25.") })
    }
}
