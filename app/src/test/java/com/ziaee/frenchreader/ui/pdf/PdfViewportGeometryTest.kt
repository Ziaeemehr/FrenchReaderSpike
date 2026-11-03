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
