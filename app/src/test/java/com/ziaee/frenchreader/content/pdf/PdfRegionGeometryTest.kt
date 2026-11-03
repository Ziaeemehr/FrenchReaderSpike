package com.ziaee.frenchreader.content.pdf

import org.junit.Assert.*
import org.junit.Test

class PdfRegionGeometryTest {
    @Test fun mapsPageCoordinatesAndClampsOutsidePoints() {
        val path = normalizeRegionPath(listOf(PdfPoint(-10f, 30f), PdfPoint(100f, 60f), PdfPoint(300f, 500f)), 200f, 300f)
        assertEquals(listOf(PdfPoint(0f, .1f), PdfPoint(.5f, .2f), PdfPoint(1f, 1f)), path)
        assertEquals(PdfRect(0f, .1f, 1f, 1f), regionBounds(path))
    }
    @Test fun rejectsEmptyAndDegenerateRegions() {
        assertNull(regionBounds(emptyList()))
        assertNull(regionBounds(listOf(PdfPoint(0f, 0f), PdfPoint(1f, 1f))))
        assertNull(regionBounds(List(3) { PdfPoint(.5f, it / 3f) }))
        assertNull(regionBounds(List(3) { PdfPoint(Float.NaN, 1f) }))
    }
    @Test fun invalidDimensionsAndPointsAreRejected() {
        for (dimension in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { normalizeRegionPath(emptyList(), dimension, 100f) }
            assertThrows(IllegalArgumentException::class.java) { normalizeRegionPath(emptyList(), 100f, dimension) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            normalizeRegionPath(listOf(PdfPoint(1f, Float.NaN)), 100f, 100f)
        }
    }
}
