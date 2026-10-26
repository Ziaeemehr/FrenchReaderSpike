package com.ziaee.frenchreader.watch

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptSelectionTest {
    private val words = listOf(0..1, 2..4, 8..12)

    @Test fun selectionSnapsInEitherDirection() {
        assertEquals(0..12, transcriptSelectionRange(words, 0, 2))
        assertEquals(0..12, transcriptSelectionRange(words, 2, 0))
        assertEquals(2..4, transcriptSelectionRange(words, 1, 1))
    }

    @Test fun substringPreservesApostrophesAndSpacing() {
        val text = "l’ami,  vient!"
        val range = transcriptSelectionRange(words, 2, 0)
        assertEquals("l’ami,  vient", text.substring(range).trim())
    }
}
