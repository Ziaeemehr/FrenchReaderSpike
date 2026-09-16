package com.ziaee.frenchreader.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalePrefsTest {
    @Test
    fun `system maps to empty locale tags`() =
        assertEquals("", AppLanguage.SYSTEM.languageTags)

    @Test
    fun `explicit languages use stable BCP 47 tags`() {
        assertEquals("fa", AppLanguage.FA.languageTags)
        assertEquals("fr", AppLanguage.FR.languageTags)
        assertEquals("en", AppLanguage.EN.languageTags)
    }

    @Test
    fun `unknown persisted value falls back to system`() =
        assertEquals(AppLanguage.SYSTEM, parseAppLanguage("UNKNOWN"))
}
