package com.ziaee.frenchreader.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoBackupTest {
    @Test
    fun fileNameIsRecognisedAndSortable() {
        val older = autoBackupFileName(1_700_000_000_000L)
        val newer = autoBackupFileName(1_700_000_060_000L)
        assertTrue(isAutoBackupFileName(older))
        assertEquals(listOf(newer, older), sortAutoBackupsNewestFirst(listOf(older, newer)))
    }

    @Test
    fun keepsNewestThreeAndFirstOfToday() {
        val names = (1..8).map { autoBackupFileName(1_700_000_000_000L + it * 60_000L) }
        val deleted = selectAutoBackupsToDelete(names.shuffled(), now = java.time.LocalDateTime.of(2023, 11, 14, 23, 59))
        // Eight backups within the same day: the three newest and the day's first one stay.
        assertEquals(names.subList(1, 5).toSet(), deleted.toSet())
    }

    @Test
    fun ignoresUnrelatedFiles() {
        val names = listOf("notes.txt", "french_reader_backup_20260925_1325.zip", autoBackupFileName(0L), "french_reader_auto_invalid.zip")
        assertEquals(emptyList<String>(), selectAutoBackupsToDelete(names, now = java.time.LocalDateTime.now()))
        assertEquals(2, sortAutoBackupsNewestFirst(names).size)
    }
    @Test fun protectedNamesAndCap() {
        val names = (1..8).map { protectedBackupFileName(1_700_000_000_000L + it * 60_000L) }
        assertTrue(names.all(::isProtectedBackupFileName))
        assertTrue(names.all { backupTimestamp(it) != null })
        assertEquals(names.take(3).toSet(), selectProtectedBackupsToDelete(names.shuffled()).toSet())
        assertEquals(emptyList<String>(), selectAutoBackupsToDelete(names))
        assertEquals(emptyList<String>(), selectProtectedBackupsToDelete(listOf("french_reader_keep_invalid.zip")))
    }
}
