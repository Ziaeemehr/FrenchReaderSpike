package com.ziaee.frenchreader.content.pdf

import org.junit.Assert.*
import org.junit.Test

class PdfHitTestTest {
    private val words = listOf(
        PdfWord("Bonjour", 0, PdfRect(.1f, .1f, .3f, .2f)),
        PdfWord("monde!", 8, PdfRect(.35f, .1f, .6f, .2f)),
        PdfWord("Au", 15, PdfRect(.1f, .3f, .2f, .4f)),
        PdfWord("revoir.", 18, PdfRect(.25f, .3f, .5f, .4f))
    )
    @Test fun rotationUsesDisplayCoordinatesWithoutDoubleRotation() {
        val b = PdfRect(20f, 30f, 60f, 50f)
        assertEquals(PdfRect(.1f, .1f, .3f, 1f / 6f), normalizePdfBounds(b, 200f, 300f, 0, 0))
        assertEquals(PdfRect(1f - 1f / 6f, .1f, .9f, .3f), normalizePdfBounds(b, 200f, 300f, 0, 90))
        assertEquals(PdfRect(.7f, 1f - 1f / 6f, .9f, .9f), normalizePdfBounds(b, 200f, 300f, 0, 180))
        assertEquals(PdfRect(.1f, .7f, 1f / 6f, .9f), normalizePdfBounds(b, 200f, 300f, 0, 270))
        assertEquals(PdfRect(20f / 300f, 30f / 200f, 60f / 300f, 50f / 200f), normalizePdfBounds(b, 200f, 300f, 90, 90))
    }

    @Test fun hitAndSlop() {
        assertEquals(words[0], hitTestWord(words, .15f, .15f, 0f))
        assertEquals(words[0], hitTestWord(words, .09f, .15f, .02f))
        assertNull(hitTestWord(words, .9f, .9f, .02f))
        assertNull(hitTestWord(emptyList(), .1f, .1f, 0f))
    }
    @Test fun sentenceBoundaries() {
        assertEquals("Bonjour monde!", sentenceAround(words, 1))
        assertEquals("Au revoir.", sentenceAround(words, 2))
        assertEquals("", sentenceAround(words, -1))
    }
    @Test fun rectangleIntersection() {
        assertEquals(words.take(2), wordsInRect(words, PdfRect(.05f, .05f, .7f, .25f)))
        assertTrue(wordsInRect(words, PdfRect(.8f, .8f, .9f, .9f)).isEmpty())
    }
}
