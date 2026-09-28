package com.ziaee.frenchreader.studylog

import com.ziaee.frenchreader.data.StudySession
import com.ziaee.frenchreader.data.StudySkill
import com.ziaee.frenchreader.data.StudySource
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyLogAggregatesTest {
    private val today = LocalDate.of(2026, 1, 7).toEpochDay()

    private fun session(
        id: Long,
        date: Long,
        minutes: Int,
        skill: StudySkill,
        source: Long? = null
    ) =
        StudySession(id, date, minutes, skill, source)

    @Test
    fun `week start supports saturday monday and year boundaries`() {
        val jan1 = LocalDate.of(2026, 1, 1).toEpochDay()
        assertEquals(
            LocalDate.of(2025, 12, 27).toEpochDay(),
            weekStart(jan1, DayOfWeek.SATURDAY)
        )
        assertEquals(
            LocalDate.of(2025, 12, 29).toEpochDay(),
            weekStart(jan1, DayOfWeek.MONDAY)
        )
    }

    @Test
    fun `period ranges include full display and elapsed days only`() {
        val week = periodRange(today, StudyPeriod.WEEK, DayOfWeek.SATURDAY)
        assertEquals(LocalDate.of(2026, 1, 3).toEpochDay(), week.start)
        assertEquals(LocalDate.of(2026, 1, 9).toEpochDay(), week.displayEnd)
        assertEquals(today, week.queryEnd)
        val month = periodRange(today, StudyPeriod.MONTH, DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 1, 1).toEpochDay(), month.start)
        assertEquals(LocalDate.of(2026, 1, 31).toEpochDay(), month.displayEnd)
        assertEquals(7, month.elapsedDays)
    }

    @Test
    fun `buckets zero fill gaps and future days`() {
        val range = periodRange(today, StudyPeriod.WEEK, DayOfWeek.SATURDAY)
        val buckets = dailyBuckets(listOf(session(1, today - 1, 30, StudySkill.CO)), range)
        assertEquals(7, buckets.size)
        assertEquals(30, buckets.single { it.epochDay == today - 1 }.totalMinutes)
        assertEquals(0, buckets.single { it.epochDay == today + 1 }.totalMinutes)
    }

    @Test
    fun `totals recompute after edit delete and include nullable source`() {
        val rows = listOf(
            session(1, today, 30, StudySkill.CO, 2),
            session(2, today, 20, StudySkill.CE)
        )
        assertEquals(30, totalsBySkill(rows)[StudySkill.CO])
        assertEquals(20, totalsBySource(rows)[null])
        val edited = listOf(rows[0].copy(durationMin = 10, skill = StudySkill.EE))
        assertEquals(10, totalsBySkill(edited)[StudySkill.EE])
        assertEquals(0, totalsBySkill(emptyList())[StudySkill.CO])
    }

    @Test
    fun `shares sum exactly one hundred and empty is zero`() {
        val shares = largestRemainderShares(
            mapOf(
                StudySkill.CO to 1,
                StudySkill.CE to 1,
                StudySkill.EE to 1
            )
        )
        assertEquals(100, shares.values.sum())
        assertEquals(listOf(34, 33, 33), StudySkill.entries.take(3).map { shares[it] })
        assertEquals(0, largestRemainderShares(emptyMap())[StudySkill.CO])
    }

    @Test
    fun `average uses elapsed days and least studied is deterministic`() {
        val range = periodRange(today, StudyPeriod.WEEK, DayOfWeek.SATURDAY)
        assertEquals(10, averagePerElapsedDay(50, range))
        assertEquals(StudySkill.CE, leastStudiedSkill(mapOf(StudySkill.CO to 1, StudySkill.CE to 0)))
        assertNull(leastStudiedSkill(StudySkill.entries.associateWith { 0 }))
    }

    @Test
    fun `daily warning excludes edited row`() {
        assertTrue(wouldExceedDailyLimit(1400, 60))
        assertFalse(wouldExceedDailyLimit(1400, 60, previousDurationMin = 60))
    }

    @Test
    fun `format and validation preserve integer minutes and local dates`() {
        assertEquals("3 h 25 min", formatMinutes(205))
        assertEquals("25 min", formatMinutes(25))
        assertEquals(SessionValidation.Valid, validateSession(1, today, today))
        assertEquals(SessionValidation.InvalidDuration, validateSession(0, today, today))
        assertEquals(SessionValidation.InvalidDuration, validateSession(1441, today, today))
        assertEquals(SessionValidation.FutureDate, validateSession(1, today + 1, today))
    }

    @Test
    fun `form helpers parse duration filter archives apply default and reset duplicate identity`() {
        assertEquals(90, parseDuration("1", "30"))
        assertNull(parseDuration("1", "75"))
        val sources = listOf(
            StudySource(1, "Active", defaultSkill = StudySkill.EE),
            StudySource(2, "Old", archived = true)
        )
        assertEquals(listOf(1L), activeSourcesForPicker(sources).map { it.id })
        assertEquals(StudySkill.EE, sources.first().defaultSkill)
        assertEquals(0L, duplicateSession(session(5, today, 30, StudySkill.CO), 99).id)
    }

    @Test
    fun `all range starts at first entry and is absent without entries`() {
        val rows = listOf(session(1, today - 20, 10, StudySkill.CO))
        assertEquals(today - 20, periodRange(today, StudyPeriod.ALL, DayOfWeek.MONDAY, rows)?.start)
        assertNull(periodRange(today, StudyPeriod.ALL, DayOfWeek.SATURDAY, emptyList()))
    }

    @Test
    fun `weekly buckets zero fill across year boundary for both week starts`() {
        val first = LocalDate.of(2025, 12, 20).toEpochDay()
        val last = LocalDate.of(2026, 1, 12).toEpochDay()
        listOf(DayOfWeek.SATURDAY, DayOfWeek.MONDAY).forEach { firstDay ->
            val buckets = weeklyBuckets(
                sessions = listOf(session(1, first, 15, StudySkill.CO)),
                start = first,
                end = last,
                firstDayOfWeek = firstDay
            )
            assertTrue(buckets.size >= 4)
            assertEquals(15, buckets.sumOf { it.totalMinutes })
            assertTrue(buckets.drop(1).any { it.totalMinutes == 0 })
        }
    }

    @Test
    fun `cumulative series are monotonic and finish at totals`() {
        val rows = listOf(
            session(1, today - 2, 20, StudySkill.CO),
            session(2, today, 30, StudySkill.CO),
            session(3, today - 1, 10, StudySkill.CE)
        )
        listOf(DayOfWeek.SATURDAY, DayOfWeek.MONDAY).forEach { firstDay ->
            val range = PeriodRange(today - 2, today, today)
            val series = cumulativeSeries(rows, range, StudyPeriod.WEEK, firstDay)
            val listening = series.getValue(StudySkill.CO)
            assertTrue(listening.zipWithNext().all { (a, b) -> b.minutes >= a.minutes })
            assertEquals(50, listening.last().minutes)
            assertEquals(10, series.getValue(StudySkill.CE).last().minutes)
        }
    }

    @Test
    fun `week comparison returns total and per skill deltas`() {
        listOf(DayOfWeek.SATURDAY, DayOfWeek.MONDAY).forEach { firstDay ->
            val currentStart = weekStart(today, firstDay)
            val rows = listOf(
                session(1, currentStart, 70, StudySkill.CO),
                session(2, currentStart - 7, 20, StudySkill.CO),
                session(3, currentStart - 6, 30, StudySkill.CE)
            )
            val comparison = weekOverWeek(rows, today, firstDay)
            assertEquals(20, comparison.totalDeltaMinutes)
            assertEquals(50, comparison.deltaBySkill[StudySkill.CO])
            assertEquals(-30, comparison.deltaBySkill[StudySkill.CE])
        }
    }

    @Test
    fun `streak keeps yesterday current until today ends and breaks on gap`() {
        val yesterdayOnly = listOf(session(1, today - 1, 20, StudySkill.CO))
        assertEquals(Streaks(longest = 1, current = 1), streaks(yesterdayOnly, today))
        val gap = listOf(
            session(1, today - 3, 20, StudySkill.CO),
            session(2, today - 1, 20, StudySkill.CO)
        )
        assertEquals(Streaks(longest = 1, current = 1), streaks(gap, today))
        assertEquals(Streaks(longest = 1, current = 0), streaks(gap, today + 2))
    }

    @Test
    fun `active days counts only days with positive totals`() {
        val rows = listOf(
            session(1, today, 20, StudySkill.CO),
            session(2, today, 10, StudySkill.CE),
            session(3, today - 2, 5, StudySkill.EE)
        )
        assertEquals(2, activeDayCount(rows))
    }

    @Test
    fun `targets cap progress and choose least percentage when configured`() {
        val totals = mapOf(StudySkill.CO to 120, StudySkill.CE to 30)
        val targets = mapOf(StudySkill.CO to 60, StudySkill.CE to 120)
        assertEquals(1f, targetProgress(120, 60))
        assertEquals(.25f, targetProgress(30, 120))
        assertEquals(StudySkill.CE, leastStudiedSkill(totals, targets))
        assertEquals(StudySkill.EE, leastStudiedSkill(totals, emptyMap()))
    }

    @Test
    fun `intensity levels use fixed minute thresholds`() {
        val minutes = listOf(0, 1, 29, 30, 59, 60, 119, 120)
        assertEquals(listOf(0, 1, 1, 2, 2, 3, 3, 4), minutes.map(::intensityLevel))
    }

    @Test
    fun `year grid aligns january first for saturday and monday`() {
        val year = 2026
        val fullyPast = LocalDate.of(2030, 1, 1).toEpochDay()
        val saturday = yearGrid(emptyList(), year, DayOfWeek.SATURDAY, fullyPast)
        val monday = yearGrid(emptyList(), year, DayOfWeek.MONDAY, fullyPast)
        assertEquals(5, saturday.first().indexOfFirst { it?.epochDay == LocalDate.of(year, 1, 1).toEpochDay() })
        assertEquals(3, monday.first().indexOfFirst { it?.epochDay == LocalDate.of(year, 1, 1).toEpochDay() })
    }

    @Test
    fun `past leap year contains all days and year grids include edge weeks`() {
        val fullyPast = LocalDate.of(2030, 1, 1).toEpochDay()
        val leap = yearGrid(emptyList(), 2028, DayOfWeek.MONDAY, fullyPast)
        assertEquals(366, leap.flatten().count { it != null })
        assertEquals(53, yearGrid(emptyList(), 2026, DayOfWeek.MONDAY, fullyPast).size)
        assertEquals(54, yearGrid(emptyList(), 2028, DayOfWeek.SUNDAY, fullyPast).size)
    }

    @Test
    fun `current year grid excludes days after today`() {
        val date = LocalDate.of(2026, 4, 12)
        val grid = yearGrid(emptyList(), 2026, DayOfWeek.MONDAY, date.toEpochDay())
        assertEquals(date.toEpochDay(), grid.flatten().filterNotNull().maxOf { it.epochDay })
    }

    @Test
    fun `month grid uses leading blanks for both week starts`() {
        val month = YearMonth.of(2026, 1)
        val fullyPast = LocalDate.of(2030, 1, 1).toEpochDay()
        val saturday = monthGrid(emptyList(), month, DayOfWeek.SATURDAY, fullyPast)
        val monday = monthGrid(emptyList(), month, DayOfWeek.MONDAY, fullyPast)
        assertEquals(5, saturday.first().takeWhile { it == null }.size)
        assertEquals(3, monday.first().takeWhile { it == null }.size)
    }

    @Test
    fun `calendar grids sum sessions on the same day`() {
        val date = LocalDate.of(2026, 1, 7)
        val rows = listOf(
            session(1, date.toEpochDay(), 29, StudySkill.CO),
            session(2, date.toEpochDay(), 31, StudySkill.CE)
        )
        val yearCell = yearGrid(rows, 2026, DayOfWeek.MONDAY, today)
            .flatten()
            .filterNotNull()
            .single { it.epochDay == date.toEpochDay() }
        val monthCell = monthGrid(rows, YearMonth.of(2026, 1), DayOfWeek.MONDAY, today)
            .flatten()
            .filterNotNull()
            .single { it.epochDay == date.toEpochDay() }
        assertEquals(60, yearCell.minutes)
        assertEquals(3, yearCell.level)
        assertEquals(60, monthCell.minutes)
    }

    @Test
    fun `year summary totals active days and longest in-year streak`() {
        val rows = listOf(
            session(1, LocalDate.of(2025, 12, 31).toEpochDay(), 90, StudySkill.CO),
            session(2, LocalDate.of(2026, 1, 1).toEpochDay(), 20, StudySkill.CO),
            session(3, LocalDate.of(2026, 1, 1).toEpochDay(), 10, StudySkill.CE),
            session(4, LocalDate.of(2026, 1, 2).toEpochDay(), 40, StudySkill.EE),
            session(5, LocalDate.of(2026, 1, 4).toEpochDay(), 50, StudySkill.EO)
        )
        assertEquals(
            YearSummary(totalMinutes = 120, activeDays = 3, longestStreak = 2),
            yearSummary(rows, 2026)
        )
    }
}
