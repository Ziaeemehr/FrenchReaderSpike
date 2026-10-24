package com.ziaee.frenchreader.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParagraphContextTest {
    @Test fun `context echo requires independent paragraph detection`() {
        org.junit.Assert.assertTrue(shouldFallbackFromParagraphContext("Bonjour.", " Bonjour. "))
        org.junit.Assert.assertFalse(shouldFallbackFromParagraphContext("Bonjour.", "Hello."))
    }

    @Test fun `keeps only the last two sentences`() {
        assertEquals("Second! Third?", paragraphTranslationContext("Next.", "First. Second! Third?"))
    }

    @Test fun `keeps an unfinished final sentence`() {
        assertEquals("Second. Still speaking", paragraphTranslationContext("Next.", "First. Second. Still speaking"))
    }

    @Test fun `supports Persian sentence punctuation`() {
        assertEquals("دوم؟ سوم!", paragraphTranslationContext("بعدی", "اول. دوم؟ سوم!"))
    }

    @Test fun `empty context is skipped`() {
        assertNull(paragraphTranslationContext("Next.", null))
        assertNull(paragraphTranslationContext("Next.", " \n "))
    }

    @Test fun `cap includes the separator and allows exactly 1800 characters`() {
        assertEquals("Context.", paragraphTranslationContext("x".repeat(1791), "Context."))
        assertNull(paragraphTranslationContext("x".repeat(1792), "Context."))
    }

    @Test fun `cap is applied after trimming to two sentences`() {
        assertEquals("Two. Three.", paragraphTranslationContext("Next.", "x".repeat(2000) + ". Two. Three."))
    }
}
