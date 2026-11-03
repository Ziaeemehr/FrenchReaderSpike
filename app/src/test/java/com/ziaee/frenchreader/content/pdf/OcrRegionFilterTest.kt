package com.ziaee.frenchreader.content.pdf

import org.junit.Assert.*
import org.junit.Test

class OcrRegionFilterTest {
    private val polygon = listOf(PdfPoint(.2f, .2f), PdfPoint(.8f, .2f), PdfPoint(.8f, .8f), PdfPoint(.2f, .8f))
    @Test fun retainsCompleteIntersectingLine() {
        assertTrue(OcrRegionFilter.keepLine(PdfRect(.2f, .1f, .8f, .5f), polygon))
        assertFalse(OcrRegionFilter.keepLine(PdfRect(0f, 0f, .1f, .1f), polygon))
    }
    @Test fun concavePolygonIsNotItsBoundingBox() {
        val triangle = listOf(PdfPoint(0f, 0f), PdfPoint(1f, 0f), PdfPoint(0f, 1f))
        assertFalse(OcrRegionFilter.contains(PdfPoint(.9f, .9f), triangle))
        assertTrue(OcrRegionFilter.contains(PdfPoint(.1f, .1f), triangle))
    }
    @Test fun marginsClampAtPageEdges() {
        assertEquals(PdfRect(0f, 0f, 1f, 1f), OcrRegionFilter.expanded(PdfRect(0f, 0f, 1f, 1f)))
    }
    @Test fun adaptiveResolutionTargetsCropAndCapsPageWidth() {
        assertEquals(3200, OcrRegionFilter.renderWidth(PdfRect(0f,0f,.5f,.2f), 1f, 400))
        assertEquals(8192, OcrRegionFilter.renderWidth(PdfRect(0f,0f,.05f,.05f), 1f, 400))
        assertEquals(1600, OcrRegionFilter.renderWidth(PdfRect(0f,0f,1f,1f), 1f, 400))
    }
    @Test fun zeroAreaAndInvalidPolygonsAreExcluded() {
        assertEquals(0f, OcrRegionFilter.overlap(PdfRect(0f,0f,0f,1f), polygon))
        assertFalse(OcrRegionFilter.contains(PdfPoint(.3f,.3f), emptyList()))
    }
    @Test fun trimsOnlyClearlyExcludedEnds() {
        val words = listOf(PdfRect(0f,.3f,.1f,.4f), PdfRect(.3f,.3f,.4f,.4f), PdfRect(.9f,.3f,1f,.4f))
        assertEquals(1..1, OcrRegionFilter.wordRange(words, polygon))
        // A vertically clipped line must keep its complete words.
        assertEquals(0..1, OcrRegionFilter.wordRange(listOf(PdfRect(.3f,0f,.4f,.1f), PdfRect(.5f,0f,.6f,.1f)), polygon))
    }

    @Test
    fun `words cut by the crop edge are trimmed from the line ends only`() {
        val polygon = listOf(PdfPoint(0f, 0f), PdfPoint(1f, 0f), PdfPoint(1f, 1f), PdfPoint(0f, 1f))
        val words = listOf(PdfRect(.1f, .1f, .2f, .2f), PdfRect(.3f, .1f, .4f, .2f), PdfRect(.5f, .1f, .6f, .2f))
        assertEquals(0..1, OcrRegionFilter.wordRange(words, polygon) { it == 2 })
        assertEquals(1..2, OcrRegionFilter.wordRange(words, polygon) { it == 0 })
        // A middle word is never dropped, even if flagged.
        assertEquals(0..2, OcrRegionFilter.wordRange(words, polygon) { it == 1 })
    }
}
