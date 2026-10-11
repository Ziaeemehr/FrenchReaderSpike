package com.ziaee.frenchreader.backup

import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRetentionTest {
    private val now = LocalDateTime.of(2026, 1, 5, 12, 0)

    @Test fun newestThreeAndSingleBackup() {
        val old = (1L..5L).map { now.minusMonths(it) }
        assertEquals(old.take(3).toSet(), selectBackupsToKeep(old, now))
        assertEquals(setOf(old.last()), selectBackupsToKeep(listOf(old.last()), now))
        assertTrue(selectBackupsToKeep(emptyList(), now).isEmpty())
    }

    @Test fun dailyKeepsNewestForSevenCalendarDays() {
        val days = (0L..6L).map { now.minusDays(it) }
        val earlier = days.map { it.minusHours(1) }
        val kept = selectBackupsToKeep(days + earlier, now)
        assertTrue(kept.containsAll(days))
        assertFalse(earlier.last() in kept)
    }

    @Test fun weeksStartMondayAcrossYearBoundary() {
        val weeks = (0L..3L).map { now.minusWeeks(it) }
        val older = now.minusWeeks(4)
        assertEquals(weeks.toSet(), selectBackupsToKeep(weeks + older, now))
    }

    @Test fun futureDatesAlwaysSurvive() {
        val future = (1L..8L).map { now.plusDays(it) }
        assertEquals(future.toSet(), selectBackupsToKeep(future, now))
    }

    @Test fun lossRequiresMoreThanTwentyPercentAndAtLeastFiveRows() {
        assertFalse(isLargeLoss(BackupCounts(25, 0, 0), BackupCounts(20, 0, 0)))
        assertTrue(isLargeLoss(BackupCounts(25, 0, 0), BackupCounts(19, 0, 0)))
        assertFalse(isLargeLoss(BackupCounts(10, 0, 0), BackupCounts(6, 0, 0)))
        assertTrue(isLargeLoss(BackupCounts(10, 0, 0), BackupCounts(5, 0, 0)))
        assertTrue(isLargeLoss(BackupCounts(0, 10, 0), BackupCounts(0, 5, 0)))
        assertTrue(isLargeLoss(BackupCounts(0, 0, 10), BackupCounts(0, 0, 5)))
        assertFalse(isLargeLoss(BackupCounts(0, 0, 0), BackupCounts(10, 10, 10)))
    }

    @Test
    fun `a month of daily use followed by a burst of backups keeps old states`() {
        // One backup every evening for 30 days, then 40 backups within two hours on the last day.
        val start = LocalDateTime.of(2026, 9, 1, 21, 0)
        var kept = emptySet<LocalDateTime>()
        fun add(at: LocalDateTime) { kept = selectBackupsToKeep(kept.toList() + at, at) }
        (0L..29L).forEach { add(start.plusDays(it)) }
        val lastDay = start.plusDays(30).withHour(9)
        (0L..39L).forEach { add(lastDay.plusMinutes(it * 3)) }
        val now = lastDay.plusMinutes(39 * 3)
        // Never grows without bound.
        assertTrue(kept.size <= 3 + 7 + 4 + 1)
        // Yesterday evening, six days ago and more than two weeks ago are all still there.
        assertTrue(start.plusDays(29) in kept)
        assertTrue(kept.any { it.toLocalDate() == now.toLocalDate().minusDays(6) })
        assertTrue(kept.any { it < now.minusDays(17) })
        // So is the first backup of the burst day, taken before most of the burst.
        assertTrue(lastDay in kept)
    }
}
