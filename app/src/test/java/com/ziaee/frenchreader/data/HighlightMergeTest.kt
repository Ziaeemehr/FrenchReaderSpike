package com.ziaee.frenchreader.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HighlightMergeTest {
    private fun highlight(start: Int, end: Int) =
        HighlightEntry(textId = 1, startOffset = start, endOffset = end, colorKey = "yellow")

    @Test
    fun keepsSelectionWhenNothingIsAbsorbed() {
        assertEquals(Pair(10, 20), mergedHighlightRange(10, 20, emptyList()))
    }

    @Test
    fun recolouringAWordInsideAHighlightCoversTheWholeHighlight() {
        assertEquals(Pair(5, 40), mergedHighlightRange(10, 20, listOf(highlight(5, 40))))
    }

    @Test
    fun spansEveryHighlightTheSelectionTouches() {
        assertEquals(Pair(2, 60), mergedHighlightRange(10, 50, listOf(highlight(2, 12), highlight(45, 60))))
    }
}
