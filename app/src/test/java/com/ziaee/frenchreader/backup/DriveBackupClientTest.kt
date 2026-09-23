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

    @Test
    fun `backup selection keeps newest five and treats legacy as oldest`() {
        val files = listOf(
            DriveBackupFile("legacy", "frenchreader_backup.db", "2099-01-01T00:00:00Z"),
            DriveBackupFile("one", "frenchreader_backup_20260918-100000.zip", "2026-09-18T10:00:00Z"),
            DriveBackupFile("two", "frenchreader_backup_20260919-100000.zip", "2026-09-19T10:00:00Z"),
            DriveBackupFile("three", "frenchreader_backup_20260920-100000.zip", "2026-09-20T10:00:00Z"),
            DriveBackupFile("four", "frenchreader_backup_20260921-100000.zip", "2026-09-21T10:00:00Z"),
            DriveBackupFile("five", "frenchreader_backup_20260922-100000.zip", "2026-09-22T10:00:00Z"),
            DriveBackupFile("six", "frenchreader_backup_20260923-100000.zip", "2026-09-23T10:00:00Z"),
            DriveBackupFile("other", "unrelated.zip", "2026-09-24T10:00:00Z")
        )

        val selection = selectDriveBackups(files)

        assertEquals("six", selection.newest?.id)
        assertEquals(listOf("six", "five", "four", "three", "two"), selection.keep.map { it.id })
        assertEquals(listOf("one", "legacy"), selection.delete.map { it.id })
    }

    @Test fun `backup selection returns no newest when only unrelated files exist`() {
        val selection = selectDriveBackups(listOf(DriveBackupFile("x", "other", "2026-01-01")))
        assertNull(selection.newest)
        assertEquals(emptyList<DriveBackupFile>(), selection.keep)
        assertEquals(emptyList<DriveBackupFile>(), selection.delete)
    }
}
