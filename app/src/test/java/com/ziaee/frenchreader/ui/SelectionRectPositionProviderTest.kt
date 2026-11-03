package com.ziaee.frenchreader.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectionRectPositionProviderTest {
    private val window = IntSize(1000, 2000)
    private val popup = IntSize(400, 100)
    private fun place(rect: Rect) = SelectionRectPositionProvider(rect, 10f)
        .calculatePosition(IntRect.Zero, window, LayoutDirection.Ltr, popup)

    @Test fun sitsAboveTheWholeSelection() =
        assertEquals(IntOffset(300, 490), place(Rect(400f, 600f, 600f, 800f)))

    @Test fun goesBelowWhenAboveWouldCoverTheSelection() =
        assertEquals(IntOffset(300, 210), place(Rect(400f, 50f, 600f, 200f)))

    @Test fun staysOnScreenWhenNeitherSideFits() =
        assertEquals(IntOffset(300, 0), place(Rect(400f, 50f, 600f, 1950f)))
}

class SemicircleLayoutTest {
    @Test fun neighboursKeepSpacingAndArcIsSymmetric() {
        val r = semicircleRadius(4, 46f)
        assertEquals(46f, r, 0.01f) // 60° apart: chord = radius
        val points = (0 until 4).map { semicirclePosition(it, 4, r) }
        assertEquals(0f, points.first().first, 0.01f); assertEquals(r, points.first().second, 0.01f)
        assertEquals(2 * r, points.last().first, 0.01f); assertEquals(r, points.last().second, 0.01f)
        for (i in 1 until 4) {
            val (x0, y0) = points[i - 1]; val (x1, y1) = points[i]
            assertEquals(46f, kotlin.math.hypot(x1 - x0, y1 - y0), 0.01f)
        }
    }
}
