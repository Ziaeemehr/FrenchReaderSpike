package com.ziaee.frenchreader.ui.pdf

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

class PdfPinchStepTest {
    private fun content(scale: Float, offset: androidx.compose.ui.geometry.Offset, screen: androidx.compose.ui.geometry.Offset) = (screen - offset) / scale

    @org.junit.Test fun pointUnderFingersFollowsThemWhileZoomingAndPanning() {
        var state = 1f to androidx.compose.ui.geometry.Offset.Zero
        var fingers = androidx.compose.ui.geometry.Offset(300f, 500f)
        val held = content(state.first, state.second, fingers)
        repeat(5) {
            val pan = androidx.compose.ui.geometry.Offset(-40f, -60f)
            state = pinchStep(state.first, state.second, 1.2f, fingers, pan, .25f, 4f)
            fingers += pan
            val now = content(state.first, state.second, fingers)
            org.junit.Assert.assertEquals(held.x, now.x, .01f)
            org.junit.Assert.assertEquals(held.y, now.y, .01f)
        }
        org.junit.Assert.assertEquals(1.2f * 1.2f * 1.2f * 1.2f * 1.2f, state.first, .001f)
    }

    @org.junit.Test fun scaleIsClampedButPanStillApplies() {
        val (scale, offset) = pinchStep(4f, androidx.compose.ui.geometry.Offset.Zero, 2f,
            androidx.compose.ui.geometry.Offset(100f, 100f), androidx.compose.ui.geometry.Offset(10f, 20f), .25f, 4f)
        org.junit.Assert.assertEquals(4f, scale)
        org.junit.Assert.assertEquals(androidx.compose.ui.geometry.Offset(10f, 20f), offset)
    }
}
