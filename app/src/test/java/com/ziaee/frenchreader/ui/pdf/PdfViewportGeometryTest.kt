package com.ziaee.frenchreader.ui.pdf

import androidx.compose.ui.geometry.Offset

import com.ziaee.frenchreader.content.pdf.PdfReadingProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfViewportGeometryTest {
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

class PdfPinchViewTest {
    private val viewport = androidx.compose.ui.geometry.Size(1000f, 2000f)
    private fun content(v: PdfView, screen: Offset) = (screen - v.pan) / v.zoom

    @org.junit.Test fun pointUnderFingersFollowsThemWhileZoomingAndMoving() {
        var view = PdfView(1.5f, Offset(-200f, -300f))
        var fingers = Offset(500f, 1000f)
        val held = content(view, fingers)
        repeat(5) {
            val move = Offset(-20f, -30f)
            val (next, overflow) = pinchView(view, 1.1f, fingers, move, viewport)
            org.junit.Assert.assertEquals(0f, overflow, .01f)
            view = next
            fingers += move
            val now = content(view, fingers)
            org.junit.Assert.assertEquals(held.x, now.x, .01f)
            org.junit.Assert.assertEquals(held.y, now.y, .01f)
        }
    }

    @org.junit.Test fun drawingAlwaysCoversTheViewportAndExtraVerticalMoveScrollsTheList() {
        val (out, overflowOut) = pinchView(PdfView(2f, Offset(-500f, -100f)), .25f, Offset(500f, 500f), Offset.Zero, viewport)
        org.junit.Assert.assertEquals(PdfView(1f, Offset.Zero), out)
        org.junit.Assert.assertEquals(200f, overflowOut, .01f) // 200 px of content wanted above the band
        val (down, overflowDown) = pinchView(PdfView(2f, Offset(-400f, -1900f)), 1f, Offset(500f, 500f), Offset(30f, -250f), viewport)
        org.junit.Assert.assertEquals(Offset(-370f, -2000f), down.pan)
        org.junit.Assert.assertEquals(-150f, overflowDown, .01f) // the rest scrolls the list forward
    }
}
