package com.ziaee.frenchreader.translate.prepare

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class PreparationSearchTest {
    @Test fun blankQueryMatchesEveryTitle() {
        assertTrue(preparationTitleMatches("élève", ""))
        assertTrue(preparationTitleMatches("", "  \t "))
    }

    @Test fun matchesCaseAndAccentsInEitherDirection() {
        assertTrue(preparationTitleMatches("Les ÉLÈVES", " eleve "))
        assertTrue(preparationTitleMatches("eleve", "ÉLÈVE"))
        assertTrue(preparationTitleMatches("Un e\u0301le\u0300ve", "élève"))
        assertTrue(preparationTitleMatches("Élève", "e\u0301le\u0300ve"))
        assertFalse(preparationTitleMatches("Élève", "professeur"))
    }

    @Test fun matchesPersianTitles() {
        assertTrue(preparationTitleMatches("داستان‌های فارسی", "فارسی"))
        assertTrue(preparationTitleMatches("سَلام", "سلام"))
        assertFalse(preparationTitleMatches("داستان فارسی", "فرانسوی"))
    }

    @Test fun caseMatchingDoesNotDependOnDeviceLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertTrue(preparationTitleMatches("HISTOIRE", "histoire"))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
