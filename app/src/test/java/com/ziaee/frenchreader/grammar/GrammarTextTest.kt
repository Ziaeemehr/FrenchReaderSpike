package com.ziaee.frenchreader.grammar

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrammarTextTest {
    @Test
    fun `Latin phrases and code are isolated in Persian prose`() {
        val source = "با (bien que + sujet → verbe) می‌آید و `j'ai étudié` درست است."
        val result = isolateGrammarText(source, rtl = true, markdown = true)
        assertTrue(result.text.contains("\u2066(bien que + sujet → verbe)\u2069"))
        // Short code spans use a no-break space so they never wrap mid-phrase.
        assertTrue(result.text.contains("\u2066j'ai\u00A0étudié\u2069"))
        assertEquals("j'ai\u00A0étudié", result.text.substring(result.code.single().start, result.code.single().end))
        assertFalse(result.text.contains('`'))
    }

    @Test
    fun `Persian fragments are isolated in French formulas`() {
        val result = isolateGrammarText("imparfait (زمینه) + passé composé", rtl = false)
        assertTrue(result.text.contains("\u2067(زمینه)\u2069"))
        assertEquals(result.text.length, result.offsets.last())
    }

    @Test
    fun `code formulas isolate Persian fragments inside their LTR isolate`() {
        val result = isolateGrammarText("`imparfait (زمینه) + passé composé`", rtl = true, markdown = true)
        assertTrue(result.text.startsWith("\u2066"))
        assertTrue(result.text.contains("\u2067(زمینه)\u2069 +"))
        assertTrue(result.text.endsWith("\u2069"))
    }

    @Test
    fun `plain text and supplementary characters keep valid UTF16 offsets`() {
        val result = isolateGrammarText("😀 été", rtl = false)
        assertEquals("😀 été", result.text)
        assertEquals((0..6).toList(), result.offsets)
    }

    @Test
    fun `highlight styles map through isolates without losing target text`() {
        val source = "😀 با été می‌آید"
        val start = source.indexOf("été")
        val annotated = grammarAnnotatedText(GrammarCell(source, listOf(HighlightRange(start, start + 3))),
            rtl = true, target = Color.Red, codeBackground = Color.Yellow)
        val span = annotated.spanStyles.single()
        assertEquals(FontWeight.Bold, span.item.fontWeight)
        assertEquals(Color.Red, span.item.color)
        assertEquals("été", annotated.text.substring(span.start, span.end).replace("\u2069", ""))
    }

    @Test
    fun longCodeSpansAreLiftedOutOfTheParagraph() {
        val segments = splitLongCode("معمولاً می‌آید: `Pendant deux ans, j'ai habité à Paris.` و `soudain` هم.")
        assertEquals(
            listOf(
                ProseSegment("معمولاً می‌آید:", false),
                ProseSegment("Pendant deux ans, j'ai habité à Paris.", true),
                ProseSegment("و `soudain` هم.", false)
            ),
            segments
        )
    }

    @Test
    fun arrowOutsideLatinRunIsMirroredInPersianText() {
        val text = isolateGrammarText("یک اتفاق است → imparfait", rtl = true).text
        assertEquals(true, text.contains('←'))
        assertEquals(false, text.contains('→'))
    }
}
