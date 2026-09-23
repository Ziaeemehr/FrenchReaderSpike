package com.ziaee.frenchreader.ui

import com.ziaee.frenchreader.shadowing.SentenceRef
import com.ziaee.frenchreader.text.MarkdownParser
import com.ziaee.frenchreader.tts.SentenceBoundary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShadowSentenceRefTest {
    private fun chunk(n: Int) = ChunkState(
        block = MarkdownParser.parse("texte"),
        sentences = List(n) { SentenceBoundary("s$it", it * 1000.0, 1000.0) }
    )
    private val image = ChunkState(block = MarkdownParser.parse("![](epubimg:x.png)"))

    @Test fun nextWithinChunk() =
        assertEquals(SentenceRef(0, 1), nextSentenceRef(listOf(chunk(2)), SentenceRef(0, 0)))

    @Test fun nextCrossesChunkAndSkipsImage() =
        assertEquals(SentenceRef(2, 0), nextSentenceRef(listOf(chunk(1), image, chunk(2)), SentenceRef(0, 0)))

    @Test fun nextIntoUnsynthesizedChunkPointsAtItsFirstSentence() =
        assertEquals(SentenceRef(1, 0), nextSentenceRef(listOf(chunk(1), chunk(0)), SentenceRef(0, 0)))

    @Test fun nextAtEndIsNull() = assertNull(nextSentenceRef(listOf(chunk(1)), SentenceRef(0, 0)))

    @Test fun previousCrossesChunkToLastSentence() =
        assertEquals(SentenceRef(0, 2), previousSentenceRef(listOf(chunk(3), image, chunk(1)), SentenceRef(2, 0)))

    @Test fun previousAtStartIsNull() = assertNull(previousSentenceRef(listOf(chunk(1)), SentenceRef(0, 0)))
}
