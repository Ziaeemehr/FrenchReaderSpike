package com.ziaee.frenchreader.ui

import com.ziaee.frenchreader.data.VocabPrefs
import org.junit.Assert.assertEquals
import org.junit.Test

class VocabReviewTranslationTest {
    @Test fun `sentence translation uses the meaning language target`() {
        assertEquals("fa", meaningTargetLanguage(VocabPrefs.MeaningLanguage.PERSIAN))
        assertEquals("en", meaningTargetLanguage(VocabPrefs.MeaningLanguage.ENGLISH))
    }
}
