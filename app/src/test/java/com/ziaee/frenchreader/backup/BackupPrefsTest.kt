package com.ziaee.frenchreader.backup

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class BackupPrefsTest {
    @Test
    fun `formatBackupTimestamp formats a timestamp using the given locale`() {
        val calendar = Calendar.getInstance(Locale.US).apply {
            set(2026, 8, 18, 14, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }

        assertEquals("Sep 18, 2026, 14:30", formatBackupTimestamp(calendar.timeInMillis, Locale.US))
    }
}
