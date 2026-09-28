package com.ziaee.frenchreader.studylog

import com.ziaee.frenchreader.data.StudySession
import com.ziaee.frenchreader.data.StudySkill
import com.ziaee.frenchreader.data.StudySource
import java.time.DayOfWeek
import java.time.LocalDate
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
}
