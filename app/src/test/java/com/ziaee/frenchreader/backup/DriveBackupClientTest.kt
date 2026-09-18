package com.ziaee.frenchreader.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DriveBackupClientTest {
    @Test
    fun `parseBackupFileId finds the matching file by name`() {
        val json = """
            {"files":[
                {"id":"abc123","name":"frenchreader_backup.db","modifiedTime":"2026-09-18T10:00:00.000Z"}
            ]}
        """.trimIndent()

        assertEquals("abc123", parseBackupFileId(json, "frenchreader_backup.db"))
    }

    @Test
    fun `parseBackupFileId returns null when no file matches`() {
        val json = """{"files":[{"id":"xyz","name":"something_else.db"}]}"""

        assertNull(parseBackupFileId(json, "frenchreader_backup.db"))
    }

    @Test
    fun `parseBackupFileId returns null when files list is empty`() {
        assertNull(parseBackupFileId("""{"files":[]}""", "frenchreader_backup.db"))
    }
}
