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
    fun keepsOnlyTheNewestBackups() {
        val names = (1..8).map { autoBackupFileName(1_700_000_000_000L + it * 60_000L) }
        val deleted = selectAutoBackupsToDelete(names.shuffled(), keep = 5)
        assertEquals(names.take(3).toSet(), deleted.toSet())
    }

    @Test
    fun ignoresUnrelatedFiles() {
        val names = listOf("notes.txt", "french_reader_backup_20260925_1325.zip", autoBackupFileName(0L))
        assertEquals(emptyList<String>(), selectAutoBackupsToDelete(names, keep = 1))
        assertEquals(1, sortAutoBackupsNewestFirst(names).size)
    }
}
