package com.ziaee.frenchreader.ui

import com.ziaee.frenchreader.tts.SentenceBoundary
import org.junit.Assert.*
import org.junit.Test

class ReadingGesturesTest {
    @Test fun tapsIgnoreSeparatorsAndOutOfBounds() {
        assertEquals(0..6, tappedWordBounds("bonjour, ami", 3))
        for (offset in listOf(-1, 7, 8, 12)) assertNull(tappedWordBounds("bonjour, ami", offset))
        assertEquals(9..11, tappedWordBounds("bonjour, ami", 10))
        assertEquals(2..12, tappedWordBounds("l’appartement", 5))
    }

    @Test fun pageStartsAtSentenceHoldingItsFirstCharacter() {
        val first = ReadingSentenceLocation(0, 0 until 12, SentenceBoundary("Un exemple.", 0.0, 100.0))
        val second = ReadingSentenceLocation(0, 13 until 20, SentenceBoundary("Bonjour", 100.0, 100.0))
        val later = ReadingSentenceLocation(1, 0 until 4, SentenceBoundary("Oui.", 0.0, 100.0))
        // A page opening mid-sentence restarts that sentence, not the next one.
        val page = ReadingPage(listOf(ReadingPageFragment(0, 6, 20, 100)))
        assertEquals(first, pagePlaybackStart(page, listOf(first, second)))
        assertEquals(second, pagePlaybackStart(ReadingPage(listOf(ReadingPageFragment(0, 13, 20, 100))), listOf(first, second)))
        // An untimed first paragraph is not skipped in favour of a later timed one.
        val untimedFirst = ReadingPage(listOf(ReadingPageFragment(2, 0, 10, 50), ReadingPageFragment(1, 0, 4, 50)))
        assertNull(pagePlaybackStart(untimedFirst, listOf(later)))
        assertTrue(pageContainsSentence(page, first))
        assertFalse(pageContainsSentence(ReadingPage(listOf(ReadingPageFragment(0, 12, 20, 100))), first))
    }
    @Test fun sentenceLocationsKeepRepeatedTextAndChunkOffsets() {
        val sentence = SentenceBoundary("Oui.", 0.0, 100.0)
        val chunk = ChunkState(
            block = com.ziaee.frenchreader.text.ParsedBlock(
                type = com.ziaee.frenchreader.text.BlockType.PARAGRAPH,
                plainText = "Oui.Oui."
            ),
            sentences = listOf(sentence, sentence.copy(offsetMs = 100.0))
        )
        val locations = readingSentenceLocations(listOf(chunk, chunk))
        assertEquals(listOf(0..3, 4..7, 0..3, 4..7), locations.map { it.range })
        assertEquals(listOf(0, 0, 1, 1), locations.map { it.chunkIndex })
    }
}
