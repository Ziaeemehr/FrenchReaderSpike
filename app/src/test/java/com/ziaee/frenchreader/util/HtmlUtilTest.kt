package com.ziaee.frenchreader.util

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlUtilTest {
    @Test
    fun `strips tags and collapses whitespace`() {
        val input = "<p>Bonjour   <br>le <b>monde</b></p>"
        assertEquals("Bonjour le monde", HtmlUtil.stripHtml(input))
    }

    @Test
    fun `unescapes common html entities`() {
        val input = "Tom &amp; Jerry &lt;3 &quot;cats&quot; &amp; dogs&#39;"
        assertEquals("Tom & Jerry <3 \"cats\" & dogs'", HtmlUtil.stripHtml(input))
    }

    @Test
    fun `trims leading and trailing whitespace`() {
        assertEquals("texte", HtmlUtil.stripHtml("   texte   "))
    }
}
