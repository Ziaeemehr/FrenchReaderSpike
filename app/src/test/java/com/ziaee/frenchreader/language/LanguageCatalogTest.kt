package com.ziaee.frenchreader.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageCatalogTest {
    @Test
    fun `unknown and null codes fall back to French`() {
        assertEquals("fr", LanguageCatalog.forCode(null).code)
        assertEquals("fr", LanguageCatalog.forCode("es").code)
    }

    @Test
    fun `every default voice belongs to its language voice list`() {
        LanguageCatalog.targets.forEach { language ->
            assertTrue(
                "${language.code} default voice must be selectable",
                language.defaultVoiceId in language.edgeVoiceIds,
            )
        }
    }

    @Test
    fun `target language codes are unique`() {
        val codes = LanguageCatalog.targets.map { it.code }

        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `French elision is only enabled for French`() {
        assertTrue(LanguageFeature.FRENCH_ELISION in LanguageCatalog.forCode("fr").features)
        assertFalse(LanguageFeature.FRENCH_ELISION in LanguageCatalog.forCode("de").features)
    }
}
