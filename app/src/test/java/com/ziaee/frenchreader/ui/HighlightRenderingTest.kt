package com.ziaee.frenchreader.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import com.ziaee.frenchreader.data.HighlightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightRenderingTest {
    private val colors = mapOf(
        "yellow" to Color.Yellow,
        "green" to Color.Green,
        "blue" to Color.Blue
    )

    @Test
    fun applyHighlightsReturnsPlainTextWhenThereAreNoHighlights() {
        val result = applyHighlights("Bonjour", emptyList(), colors)

        assertEquals("Bonjour", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun applyHighlightsKeepsAdjacentRangesSeparate() {
        val result = applyHighlights(
            text = "Bonjour monde",
            highlights = listOf(
                highlight(id = 1, start = 0, end = 7, colorKey = "yellow"),
                highlight(id = 2, start = 7, end = 13, colorKey = "green")
            ),
            colors = colors
        )

        assertEquals(
            listOf(
                androidx.compose.ui.text.AnnotatedString.Range(SpanStyle(background = Color.Yellow), 0, 7),
                androidx.compose.ui.text.AnnotatedString.Range(SpanStyle(background = Color.Green), 7, 13)
            ),
            result.spanStyles
        )
    }

    @Test
    fun applyHighlightsPreservesOverlappingRangesInInputOrder() {
        val result = applyHighlights(
            text = "Bonjour monde",
            highlights = listOf(
                highlight(id = 1, start = 0, end = 10, colorKey = "yellow"),
                highlight(id = 2, start = 3, end = 13, colorKey = "blue")
            ),
            colors = colors
        )

        assertEquals(0, result.spanStyles[0].start)
        assertEquals(10, result.spanStyles[0].end)
        assertEquals(Color.Yellow, result.spanStyles[0].item.background)
        assertEquals(3, result.spanStyles[1].start)
        assertEquals(13, result.spanStyles[1].end)
        assertEquals(Color.Blue, result.spanStyles[1].item.background)
    }

    @Test
    fun applyHighlightsClampsRangesAndSkipsEmptyOrUnknownColors() {
        val result = applyHighlights(
            text = "Bonjour",
            highlights = listOf(
                highlight(id = 1, start = -4, end = 99, colorKey = "yellow"),
                highlight(id = 2, start = 4, end = 4, colorKey = "green"),
                highlight(id = 3, start = 0, end = 3, colorKey = "missing")
            ),
            colors = colors
        )

        assertEquals(1, result.spanStyles.size)
        assertEquals(0, result.spanStyles.single().start)
        assertEquals(7, result.spanStyles.single().end)
    }

    private fun highlight(id: Long, start: Int, end: Int, colorKey: String) = HighlightEntry(
        id = id,
        textId = 10,
        startOffset = start,
        endOffset = end,
        colorKey = colorKey,
        createdAtMs = id
    )
}
