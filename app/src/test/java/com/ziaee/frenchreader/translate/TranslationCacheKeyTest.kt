package com.ziaee.frenchreader.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import java.security.MessageDigest
import org.junit.Test

class TranslationCacheKeyTest {
    @Test fun `cache key includes source and target language`() {
        assertNotEquals(
            translationCacheKey("Gift", "de", "en"),
            translationCacheKey("Gift", "fr", "en")
        )
        assertNotEquals(
            translationCacheKey("Gift", "de", "en"),
            translationCacheKey("Gift", "de", "fa")
        )
    }

    @Test fun `french keeps the legacy key so old cached translations are still found`() {
        val legacy = MessageDigest.getInstance("SHA-256").digest("Bonjour|fa".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        assertEquals(legacy, translationCacheKey("Bonjour", "fr", "fa"))
    }
}
