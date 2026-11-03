package com.ziaee.frenchreader.content.pdf

import org.junit.Assert.*
import org.junit.Test

class PdfOcrBodyTest {
    private val placeholders = listOf(
        "Scanned PDF. Switch to PDF view to read the pages.",
        "PDF numérisé. Passez à la vue PDF pour lire les pages.",
        "پی‌دی‌اف اسکن‌شده. برای خواندن صفحه‌ها نمای پی‌دی‌اف را باز کنید."
    )
    @Test fun replacesExactLegacyPlaceholderInEveryLocale() {
        placeholders.forEach {
            assertTrue(isScannedPdfPlaceholder(it))
            assertEquals("Bonjour !", appendPdfOcrText(it, " Bonjour ! "))
        }
    }
    @Test fun preservesExistingBodiesIncludingEditedPlaceholders() {
        (placeholders.map { it + "\nMy notes" } + listOf("Mon texte", " " + placeholders.first())).forEach {
            assertFalse(isScannedPdfPlaceholder(it))
            assertEquals(it + "\n\nBonjour", appendPdfOcrText(it, "Bonjour"))
        }
    }
    @Test fun emptyOcrDoesNotModifyBodyAndEmptyBodyGetsText() {
        assertEquals("Old text", appendPdfOcrText("Old text", "  "))
        assertEquals("Bonjour", appendPdfOcrText("", "Bonjour"))
        assertEquals("First\n\nSecond", appendPdfOcrText(appendPdfOcrText("", "First"), "Second"))
    }
}
