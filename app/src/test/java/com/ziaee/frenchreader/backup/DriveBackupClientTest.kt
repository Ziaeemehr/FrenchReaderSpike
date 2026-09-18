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

    @Test
    fun `buildMultipartUploadBody produces correct multipart structure`() {
        val body = buildMultipartUploadBody(
            metadataJson = """{"name":"frenchreader_backup.db"}""",
            fileBytes = "FAKE_DB_BYTES".toByteArray(Charsets.UTF_8),
            boundary = "test-boundary"
        )
        val text = String(body, Charsets.UTF_8)

        assertEquals(true, text.startsWith("--test-boundary\r\n"))
        assertEquals(
            true,
            text.contains("Content-Type: application/json; charset=UTF-8\r\n\r\n{\"name\":\"frenchreader_backup.db\"}\r\n")
        )
        assertEquals(true, text.contains("Content-Type: application/octet-stream\r\n\r\nFAKE_DB_BYTES"))
        assertEquals(true, text.endsWith("\r\n--test-boundary--\r\n"))
    }
}
