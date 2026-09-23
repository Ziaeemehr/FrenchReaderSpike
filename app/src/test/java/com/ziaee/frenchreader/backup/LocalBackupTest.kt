package com.ziaee.frenchreader.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        assertFalse(isSupportedDatabaseVersion(1, 13))
        assertTrue(isSupportedDatabaseVersion(2, 13))
        assertTrue(isSupportedDatabaseVersion(13, 13))
        assertFalse(isSupportedDatabaseVersion(14, 13))
    }
}
