package com.ziaee.frenchreader.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguagePrefsTest {
    @Test
    fun `legacy Persian meaning language maps to fa`() {
        assertEquals("fa", legacyMeaningLanguageCode(VocabPrefs.MeaningLanguage.PERSIAN))
    }

    @Test
    fun `legacy English meaning language maps to en`() {
        assertEquals("en", legacyMeaningLanguageCode(VocabPrefs.MeaningLanguage.ENGLISH))
    }
}
