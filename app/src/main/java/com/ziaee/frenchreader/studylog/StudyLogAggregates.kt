package com.ziaee.frenchreader.studylog

import com.ziaee.frenchreader.data.StudySession
import com.ziaee.frenchreader.data.StudySkill
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.floor

enum class StudyPeriod {
    WEEK,
    MONTH
}

data class PeriodRange(
    val start: Long,
    val queryEnd: Long,
    val displayEnd: Long
) {
    val elapsedDays: Int
        get() = (queryEnd - start + 1).coerceAtLeast(0).toInt()
}

data class DailyStudyBucket(
    val epochDay: Long,
    val minutesBySkill: Map<StudySkill, Int>
) {
    val totalMinutes: Int
        get() = minutesBySkill.values.sum()
}

internal fun weekStart(epochDay: Long, firstDayOfWeek: DayOfWeek): Long {
    val date = LocalDate.ofEpochDay(epochDay)
    val days = (date.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    return date.minusDays(days.toLong()).toEpochDay()
}

internal fun periodRange(today: Long, period: StudyPeriod, firstDayOfWeek: DayOfWeek): PeriodRange {
    val date = LocalDate.ofEpochDay(today)
    return when (period) {
        StudyPeriod.WEEK -> {
            val start = weekStart(today, firstDayOfWeek)
            PeriodRange(
                start = start,
                queryEnd = today,
                displayEnd = start + 6
            )
        }
        StudyPeriod.MONTH -> PeriodRange(
            start = date.withDayOfMonth(1).toEpochDay(),
            queryEnd = today,
            displayEnd = date.with(TemporalAdjusters.lastDayOfMonth()).toEpochDay()
        )
    }
}

internal fun dailyBuckets(sessions: List<StudySession>, range: PeriodRange): List<DailyStudyBucket> {
    val grouped = sessions.groupBy { it.date }
    return (range.start..range.displayEnd).map { day ->
        DailyStudyBucket(day, totalsBySkill(grouped[day].orEmpty()))
    }
}

internal fun totalsBySkill(sessions: List<StudySession>): Map<StudySkill, Int> =
    StudySkill.entries.associateWith { skill -> sessions.filter { it.skill == skill }.sumOf { it.durationMin } }

internal fun totalsBySource(sessions: List<StudySession>): Map<Long?, Int> =
    sessions.groupingBy { it.sourceId }
        .fold(0) { total, row -> total + row.durationMin }

internal fun largestRemainderShares(totals: Map<StudySkill, Int>): Map<StudySkill, Int> {
    val total = totals.values.sum()
    if (total <= 0) return StudySkill.entries.associateWith { 0 }
    val exact = StudySkill.entries.associateWith { skill ->
        (totals[skill] ?: 0) * 100.0 / total
    }
    val result = exact.mapValuesTo(mutableMapOf()) { floor(it.value).toInt() }
    repeat(100 - result.values.sum()) {
        val skill = StudySkill.entries.maxWith(
            compareBy<StudySkill> { exact.getValue(it) - result.getValue(it) }
                .thenBy { -it.ordinal }
        )
        result[skill] = result.getValue(skill) + 1
    }
    return result
}

internal fun averagePerElapsedDay(totalMinutes: Int, range: PeriodRange): Int =
    if (range.elapsedDays == 0) 0 else totalMinutes / range.elapsedDays

internal fun leastStudiedSkill(totals: Map<StudySkill, Int>): StudySkill? {
    val complete = StudySkill.entries.associateWith { totals[it] ?: 0 }
    if (complete.values.all { it == 0 }) return null
    return StudySkill.entries.minBy { complete.getValue(it) }
}

internal fun formatMinutes(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

sealed interface SessionValidation {
    data object Valid : SessionValidation
    data object InvalidDuration : SessionValidation
    data object FutureDate : SessionValidation
}

internal fun validateSession(durationMin: Int, date: Long, today: Long): SessionValidation = when {
    durationMin !in 1..1440 -> SessionValidation.InvalidDuration
    date > today -> SessionValidation.FutureDate
    else -> SessionValidation.Valid
}

internal fun wouldExceedDailyLimit(
    existingMinutes: Int,
    newMinutes: Int,
    previousDurationMin: Int = 0
): Boolean =
    existingMinutes - previousDurationMin + newMinutes > 1440

internal fun activeSourcesForPicker(
    sources: List<com.ziaee.frenchreader.data.StudySource>
) = sources.filterNot { it.archived }

internal fun parseDuration(hours: String, minutes: String): Int? {
    val h = hours.toIntOrNull() ?: return null
    val m = minutes.toIntOrNull() ?: return null
    if (h < 0 || m !in 0..59) return null
    return h * 60 + m
}

internal fun duplicateSession(
    session: StudySession,
    now: Long = System.currentTimeMillis()
) =
    session.copy(id = 0, createdAt = now, updatedAt = now)
