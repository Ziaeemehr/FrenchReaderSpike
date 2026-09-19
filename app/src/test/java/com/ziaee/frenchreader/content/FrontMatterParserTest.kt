package com.ziaee.frenchreader.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrontMatterParserTest {
    @Test
    fun `extracts title and removes yaml front matter`() {
        val parsed = parseFrontMatter("---\ntitle: Une journée à Paris\nauthor: Amélie\n---\n# Introduction\n\nBonjour")

        assertEquals("Une journée à Paris", parsed.title)
        assertEquals("# Introduction\n\nBonjour", parsed.body)
    }

    @Test
    fun `unquotes quoted title`() {
        assertEquals("Le voyage", parseFrontMatter("---\ntitle: \"Le voyage\"\n---\nTexte").title)
        assertEquals("L'été", parseFrontMatter("---\ntitle: 'L''été'\n---\nTexte").title)
    }

    @Test
    fun `returns original body when front matter is absent or unterminated`() {
        val markdown = "# Heading\n\nBody"
        assertEquals(ParsedFrontMatter(null, markdown), parseFrontMatter(markdown))
        assertEquals(ParsedFrontMatter(null, "---\ntitle: Draft\nBody"), parseFrontMatter("---\ntitle: Draft\nBody"))
    }

    @Test
    fun `front matter without title is still removed`() {
        val parsed = parseFrontMatter("---\nauthor: Jules\n---\nBody")

        assertNull(parsed.title)
        assertEquals("Body", parsed.body)
    }
}
