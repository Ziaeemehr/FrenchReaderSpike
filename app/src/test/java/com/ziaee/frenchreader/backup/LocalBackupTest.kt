package com.ziaee.frenchreader.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalBackupTest {
    @Test
    fun `valid SQLite header is accepted`() {
        val bytes = "SQLite format 3\u0000remaining database bytes".toByteArray()

        assertTrue(isValidSqliteBackup(bytes))
    }

    @Test
    fun `short or incorrect headers are rejected`() {
        assertFalse(isValidSqliteBackup("SQLite format 3".toByteArray()))
        assertFalse(isValidSqliteBackup("Not a SQLite database".toByteArray()))
        assertFalse(isValidSqliteBackup(byteArrayOf()))
    }

    @Test
    fun `supported database versions are limited to migratable range`() {
        assertFalse(isSupportedDatabaseVersion(1, 14))
        assertTrue(isSupportedDatabaseVersion(2, 14))
        assertTrue(isSupportedDatabaseVersion(14, 14))
        assertFalse(isSupportedDatabaseVersion(15, 14))
    }

    @Test fun `settings JSON round trips preference value types`() {
        val settings = mapOf(
            "vocab_prefs" to mapOf<String, Any?>(
                "string" to "value", "int" to 3, "long" to 4L,
                "float" to 1.5f, "boolean" to true, "set" to setOf("a", "b")
            ),
            "locale_prefs" to emptyMap()
        )

        assertEquals(settings, deserializeSettings(serializeSettings(settings)))
    }
}
