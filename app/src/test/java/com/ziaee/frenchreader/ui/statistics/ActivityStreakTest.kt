package com.ziaee.frenchreader.ui.statistics

import com.ziaee.frenchreader.data.DailyActivityRow
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityStreakTest {
    private val today = LocalDate.of(2026, 10, 26)

    @Test fun `goal must be met including its exact threshold`() {
        assertEquals(0, computeActivityStreak(mapOf(today to 4.999), setOf(today), today, 5, today))
        assertEquals(1, computeActivityStreak(mapOf(today to 5.0), emptySet(), today, 5, today))
        assertEquals(0, computeActivityStreak(mapOf(today to 5.0), emptySet(), today, 10, today))
        assertEquals(1, computeActivityStreak(mapOf(today to 3.0), emptySet(), today, 3, today))
    }

    @Test fun `cutover uses legacy dates only before the boundary`() {
        val legacy = setOf(today, today.minusDays(1), today.minusDays(2))
        assertEquals(3, computeActivityStreak(mapOf(today to 5.0), legacy, today, 5, today))
        assertEquals(2, computeActivityStreak(emptyMap(), legacy, today, 5, today))
        // An unmet day on the cutover breaks the chain, even if legacy activity exists.
        assertEquals(1, computeActivityStreak(mapOf(today to 5.0), legacy, today.minusDays(1), 5, today))
        // Minutes alone must never invent a legacy active day.
        assertEquals(1, computeActivityStreak(mapOf(today to 5.0, today.minusDays(1) to 20.0), emptySet(), today, 5, today))
    }

    @Test fun `today has grace but an unmet yesterday breaks the streak`() {
        val minutes = mapOf(today to 2.0, today.minusDays(1) to 5.0, today.minusDays(2) to 6.0)
        assertEquals(2, computeActivityStreak(minutes, emptySet(), today.minusDays(10), 5, today))
        assertEquals(3, computeActivityStreak(minutes + (today to 5.0), emptySet(), today.minusDays(10), 5, today))
        assertEquals(0, computeActivityStreak(minutes - today.minusDays(1), emptySet(), today.minusDays(10), 5, today))
        assertEquals(0, computeActivityStreak(emptyMap(), emptySet(), today, 5, today))
    }

    @Test fun `mixed source durations sum without truncating fractional minutes`() {
        val rows = listOf(60_000L, 70_000L, 80_000L, 10_000L, 20_000L, 60_000L)
            .map { DailyActivityRow(today.toString(), it, false) }
        val minutes = dailyActivityMinutes(rows + DailyActivityRow(today.minusDays(1).toString(), 10_000, true))
        assertEquals(5.0, minutes.getValue(today), 0.00001)
        assertEquals(1.0 / 6, minutes.getValue(today.minusDays(1)), 0.00001)
        assertEquals(1, computeActivityStreak(minutes, emptySet(), today, 5, today))
    }

    @Test fun `week minutes respect configured start and exclude future days`() {
        val minutes = mapOf(today.minusDays(1) to 10.0, today to 5.5, today.plusDays(1) to 20.0)
        assertEquals(5.5, activityWeekMinutes(minutes, today, DayOfWeek.MONDAY), 0.00001)
        assertEquals(15.5, activityWeekMinutes(minutes, today, DayOfWeek.SATURDAY), 0.00001)
    }
}
