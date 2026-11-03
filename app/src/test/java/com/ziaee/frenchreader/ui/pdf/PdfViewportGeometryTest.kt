package com.ziaee.frenchreader.ui.pdf

import androidx.compose.ui.geometry.Offset

import com.ziaee.frenchreader.content.pdf.PdfReadingProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfViewportGeometryTest {
    @Test
    fun `zooming keeps the content under the focal point`() {
        // Content at scroll 100 + focal 50 = 150; doubling puts it at 300, still under the finger.
        assertEquals(250f, focalScrollOffset(scroll = 100f, focal = 50f, oldZoom = 1f, newZoom = 2f), 0.001f)
        assertEquals(100f, focalScrollOffset(scroll = 250f, focal = 50f, oldZoom = 2f, newZoom = 1f), 0.001f)
    }

    @Test
    fun `page at centre picks the page covering the viewport middle`() {
        val pages = listOf(PdfVisiblePage(0, -900, 1000), PdfVisiblePage(1, 120, 1000))
        assertEquals(1, pageAtCentre(pages, 0, 1000))
        assertEquals(0, pageAtCentre(listOf(PdfVisiblePage(0, -100, 1000), PdfVisiblePage(1, 920, 1000)), 0, 1000))
        assertEquals(null, pageAtCentre(emptyList(), 0, 1000))
    }

    @Test
    fun `pdf progress is finished only on the last known page`() {
        assertTrue(PdfReadingProgress(page = 4, pageCount = 5).finished)
        assertFalse(PdfReadingProgress(page = 3, pageCount = 5).finished)
        assertFalse(PdfReadingProgress(page = 0, pageCount = 0).finished)
        assertFalse(PdfReadingProgress(page = -1, pageCount = 5).finished)
    }
}

class PdfPinchTargetTest {
    private val width = 1000f
    private fun height(page: Int, zoom: Float) = width * zoom * 1.4f + 20f
    // Content position (unzoomed px) of a screen point: sideways and down from the top of page 0.
    private fun content(t: PdfPinchTarget, screen: Offset) =
        Offset((t.horizontal + screen.x) / t.zoom, ((0 until t.page).sumOf { height(it, t.zoom).toDouble() }.toFloat() + t.pageOffset + screen.y) / t.zoom)

    @org.junit.Test fun pointUnderFingersFollowsThemAcrossFrames() {
        var t = PdfPinchTarget(1.5f, 2, 300f, 200f)
        var fingers = Offset(400f, 600f)
        val held = content(t, fingers)
        repeat(6) {
            val pan = Offset(-30f, -45f)
            t = pinchTarget(t, 1.1f, fingers, pan, width, 10, ::height)
            fingers += pan
            val now = content(t, fingers)
            // The fixed 20 px gap does not scale, so allow a few px of drift per page boundary.
            org.junit.Assert.assertEquals(held.x, now.x, .5f)
            org.junit.Assert.assertEquals(held.y, now.y, 40f)
        }
        org.junit.Assert.assertEquals(1.5f * Math.pow(1.1, 6.0).toFloat(), t.zoom, .01f)
    }

    @org.junit.Test fun panAtMaxZoomStillMovesAndStaysInBounds() {
        val t = pinchTarget(PdfPinchTarget(4f, 0, 0f, 0f), 2f, Offset(100f, 100f), Offset(50f, 80f), width, 3, ::height)
        org.junit.Assert.assertEquals(4f, t.zoom)
        org.junit.Assert.assertEquals(0f, t.horizontal) // cannot scroll left of the page
        org.junit.Assert.assertEquals(0f, t.pageOffset) // nor above the first page
        val down = pinchTarget(PdfPinchTarget(2f, 0, 2700f, 500f), 1f, Offset(0f, 0f), Offset(-100f, -200f), width, 3, ::height)
        org.junit.Assert.assertEquals(1, down.page) // moved past page 0 (height 2820)
        org.junit.Assert.assertEquals(80f, down.pageOffset, .01f)
        org.junit.Assert.assertEquals(600f, down.horizontal, .01f)
    }
}
