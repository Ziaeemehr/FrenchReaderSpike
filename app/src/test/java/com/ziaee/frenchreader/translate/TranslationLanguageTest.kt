package com.ziaee.frenchreader.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationLanguageTest {
    @Test
    fun `base language drops the region`() {
        assertEquals("fa", TranslationService.baseLanguage("fa-IR"))
        assertEquals("zh", TranslationService.baseLanguage("zh_CN"))
        assertEquals("en", TranslationService.baseLanguage("EN"))
    }

    @Test
    fun `an echoed translation counts as the same text`() {
        assertTrue(TranslationService.isSameText("Hello, world!", "hello world"))
        assertTrue(TranslationService.isSameText("متن سؤال", "📩 متن سؤال"))
        assertFalse(TranslationService.isSameText("Bonjour", "Hello"))
    }
}
