package com.ziaee.frenchreader.content.pdf

import com.ziaee.frenchreader.content.OcrDocument
import com.ziaee.frenchreader.content.OcrLine
import com.ziaee.frenchreader.content.OcrWord
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfPageOcrTest {
    private fun word(text: String, l: Float, t: Float, r: Float, b: Float) = OcrWord(text, PdfRect(l, t, r, b), emptyList(), null)

    @Test fun wordBoxesBecomePageFractionsAndTapsHitThem() {
        val doc = OcrDocument(listOf(listOf(
            OcrLine("Le chat dort.", null, emptyList(), null, listOf(
                word("Le", 100f, 200f, 160f, 240f), word("chat", 180f, 200f, 300f, 240f), word("dort.", 320f, 200f, 440f, 240f))),
            OcrLine("Il rêve", null, emptyList(), null, listOf(
                word("Il", 100f, 300f, 140f, 340f), word("rnange", 160f, 300f, 330f, 340f))))))
        val words = ocrPageWords(doc, 1000f, 2000f) { text, _ -> if (text == "rnange") "mange" else text }
        assertEquals(listOf("Le", "chat", "dort.", "Il", "mange"), words.map { it.text })
        assertEquals(PdfRect(.18f, .1f, .3f, .12f), words[1].bounds)
        assertEquals(listOf(0, 3, 8, 14, 17), words.map { it.textIndex })
        assertEquals("chat", hitTestWord(words, .2f, .11f)?.text)
        assertEquals("Le chat dort.", sentenceAround(words, 1))
    }

    @Test fun cacheRoundTripKeepsWordsAndOffsets() {
        val words = listOf(PdfWord("écoute", 0, PdfRect(.1f, .2f, .3f, .25f)), PdfWord("bien", 7, PdfRect(.32f, .2f, .4f, .25f)))
        assertEquals(words, decodeOcrWords(encodeOcrWords(words)))
        assertEquals(emptyList<PdfWord>(), decodeOcrWords(encodeOcrWords(emptyList())))
    }
}
