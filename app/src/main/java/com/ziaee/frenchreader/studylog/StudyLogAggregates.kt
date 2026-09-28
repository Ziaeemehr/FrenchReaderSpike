package com.ziaee.frenchreader.studylog

import com.ziaee.frenchreader.data.StudySession
import com.ziaee.frenchreader.data.StudySkill
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import kotlin.math.floor

enum class StudyPeriod {
    WEEK,
    MONTH,
    ALL
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

data class CumulativePoint(val epochDay: Long, val minutes: Int)

data class WeekComparison(
    val currentMinutes: Int,
    val previousMinutes: Int,
    val deltaBySkill: Map<StudySkill, Int>
) {
    val totalDeltaMinutes: Int
        get() = currentMinutes - previousMinutes
}

data class Streaks(val longest: Int, val current: Int)

data class ActivityDay(
    val epochDay: Long,
    val minutes: Int,
    val level: Int
)

data class YearSummary(
    val totalMinutes: Int,
    val activeDays: Int,
    val longestStreak: Int
)

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
        StudyPeriod.ALL -> PeriodRange(today, today, today)
    }
}

internal fun periodRange(
    today: Long,
    period: StudyPeriod,
    firstDayOfWeek: DayOfWeek,
    sessions: List<StudySession>
): PeriodRange? = periodRange(today, period, firstDayOfWeek, sessions.minOfOrNull { it.date })

internal fun periodRange(
    today: Long,
    period: StudyPeriod,
    firstDayOfWeek: DayOfWeek,
    firstEntryDate: Long?
): PeriodRange? = if (period == StudyPeriod.ALL) {
    firstEntryDate?.let { PeriodRange(it, today, today) }
} else {
    periodRange(today, period, firstDayOfWeek)
}

internal fun dailyBuckets(sessions: List<StudySession>, range: PeriodRange): List<DailyStudyBucket> {
    val grouped = sessions.groupBy { it.date }
    return (range.start..range.displayEnd).map { day ->
        DailyStudyBucket(day, totalsBySkill(grouped[day].orEmpty()))
    }
}

internal fun weeklyBuckets(
    sessions: List<StudySession>,
    start: Long,
    end: Long,
    firstDayOfWeek: DayOfWeek
): List<DailyStudyBucket> {
    val firstWeek = weekStart(start, firstDayOfWeek)
    val lastWeek = weekStart(end, firstDayOfWeek)
    val grouped = sessions.groupBy { weekStart(it.date, firstDayOfWeek) }
    return generateSequence(firstWeek) { current ->
        (current + 7).takeIf { it <= lastWeek }
    }.map { day ->
        DailyStudyBucket(day, totalsBySkill(grouped[day].orEmpty()))
    }.toList()
}

internal fun periodBuckets(
    sessions: List<StudySession>,
    range: PeriodRange,
    period: StudyPeriod,
    firstDayOfWeek: DayOfWeek
): List<DailyStudyBucket> = if (period == StudyPeriod.ALL) {
    weeklyBuckets(sessions, range.start, range.queryEnd, firstDayOfWeek)
} else {
    dailyBuckets(sessions, range)
}

internal fun cumulativeSeries(
    sessions: List<StudySession>,
    range: PeriodRange,
    period: StudyPeriod,
    firstDayOfWeek: DayOfWeek
): Map<StudySkill, List<CumulativePoint>> {
    val cumulativeRange = range.copy(displayEnd = range.queryEnd)
    val buckets = periodBuckets(sessions, cumulativeRange, period, firstDayOfWeek)
    return StudySkill.entries.associateWith { skill ->
        var running = 0
        buckets.map { bucket ->
            running += bucket.minutesBySkill[skill] ?: 0
            CumulativePoint(bucket.epochDay, running)
        }
    }
}

internal fun intensityLevel(minutes: Int): Int = when {
    minutes <= 0 -> 0
    minutes < 30 -> 1
    minutes < 60 -> 2
    minutes < 120 -> 3
    else -> 4
}

internal fun yearGrid(
    sessions: List<StudySession>,
    year: Int,
    firstDayOfWeek: DayOfWeek,
    today: Long
): List<List<ActivityDay?>> {
    val firstDate = LocalDate.of(year, 1, 1)
    val lastDate = LocalDate.of(year, 12, 31)
    val gridStart = weekStart(firstDate.toEpochDay(), firstDayOfWeek)
    val gridEnd = weekStart(lastDate.toEpochDay(), firstDayOfWeek) + 6
    return calendarGrid(sessions, gridStart, gridEnd, firstDate, lastDate, today)
}

internal fun monthGrid(
    sessions: List<StudySession>,
    yearMonth: YearMonth,
    firstDayOfWeek: DayOfWeek,
    today: Long
): List<List<ActivityDay?>> {
    val firstDate = yearMonth.atDay(1)
    val lastDate = yearMonth.atEndOfMonth()
    val gridStart = weekStart(firstDate.toEpochDay(), firstDayOfWeek)
    val gridEnd = weekStart(lastDate.toEpochDay(), firstDayOfWeek) + 6
    return calendarGrid(sessions, gridStart, gridEnd, firstDate, lastDate, today)
}

private fun calendarGrid(
    sessions: List<StudySession>,
    gridStart: Long,
    gridEnd: Long,
    firstDate: LocalDate,
    lastDate: LocalDate,
    today: Long
): List<List<ActivityDay?>> {
    val totals = sessions.groupingBy { it.date }.fold(0) { total, session ->
        total + session.durationMin
    }
    return (gridStart..gridEnd).map { epochDay ->
        if (epochDay !in firstDate.toEpochDay()..lastDate.toEpochDay() || epochDay > today) {
            null
        } else {
            val minutes = totals[epochDay] ?: 0
            ActivityDay(epochDay, minutes, intensityLevel(minutes))
        }
    }.chunked(7)
}

internal fun yearSummary(sessions: List<StudySession>, year: Int): YearSummary {
    val first = LocalDate.of(year, 1, 1).toEpochDay()
    val last = LocalDate.of(year, 12, 31).toEpochDay()
    val totals = sessions.asSequence()
        .filter { it.date in first..last }
        .groupingBy { it.date }
        .fold(0) { total, session -> total + session.durationMin }
    val activeDays = totals.filterValues { it > 0 }.keys.sorted()
    var longest = 0
    var current = 0
    var previous: Long? = null
    activeDays.forEach { day ->
        current = if (previous != null && day == previous!! + 1) current + 1 else 1
        longest = maxOf(longest, current)
        previous = day
    }
    return YearSummary(
        totalMinutes = totals.values.sum(),
        activeDays = activeDays.size,
        longestStreak = longest
    )
}

internal fun weekOverWeek(
    sessions: List<StudySession>,
    today: Long,
    firstDayOfWeek: DayOfWeek
): WeekComparison {
    val currentStart = weekStart(today, firstDayOfWeek)
    val elapsed = (today - currentStart).toInt()
    val previousStart = currentStart - 7
    val current = sessions.filter { it.date in currentStart..today }
    val previous = sessions.filter { it.date in previousStart..(previousStart + elapsed) }
    val currentBySkill = totalsBySkill(current)
    val previousBySkill = totalsBySkill(previous)
    return WeekComparison(
        currentMinutes = current.sumOf { it.durationMin },
        previousMinutes = previous.sumOf { it.durationMin },
        deltaBySkill = StudySkill.entries.associateWith {
            currentBySkill.getValue(it) - previousBySkill.getValue(it)
        }
    )
}

internal fun activeDayCount(sessions: List<StudySession>): Int =
    sessions.groupBy { it.date }.count { (_, rows) -> rows.sumOf { it.durationMin } > 0 }

internal fun streaks(sessions: List<StudySession>, today: Long): Streaks {
    val active = sessions.groupBy { it.date }
        .filterValues { rows -> rows.sumOf { it.durationMin } > 0 }
        .keys
        .sorted()
    var longest = 0
    var run = 0
    var previous: Long? = null
    active.forEach { day ->
        run = if (previous != null && day == previous!! + 1) run + 1 else 1
        longest = maxOf(longest, run)
        previous = day
    }
    val end = active.lastOrNull()
    val current = if (end == today || end == today - 1) run else 0
    return Streaks(longest, current)
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
    val currentSkills = StudySkill.entries.filterNot { it.legacy }
    val complete = currentSkills.associateWith { totals[it] ?: 0 }
    if (totals.values.all { it == 0 }) return null
    return currentSkills.minBy { complete.getValue(it) }
}

internal fun leastStudiedSkill(
    totals: Map<StudySkill, Int>,
    targets: Map<StudySkill, Int>
): StudySkill? {
    val configured = targets.filter { (skill, value) -> !skill.legacy && value > 0 }
    if (configured.isEmpty()) return leastStudiedSkill(totals)
    return configured.keys.minWith(
        compareBy<StudySkill> { (totals[it] ?: 0).toDouble() / configured.getValue(it) }
            .thenBy { it.ordinal }
    )
}

internal fun targetProgress(minutes: Int, targetMinutes: Int): Float =
    if (targetMinutes <= 0) 0f else (minutes.toFloat() / targetMinutes).coerceIn(0f, 1f)

internal fun relativeProgress(minutes: Int, largestMinutes: Int): Float =
    if (largestMinutes <= 0) 0f else (minutes.toFloat() / largestMinutes).coerceIn(0f, 1f)

internal fun formatMinutes(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

sealed interface SessionValidation {
    data object Valid : SessionValidation
    data object InvalidDuration : SessionValidation
    data object FutureDate : SessionValidation

    data object MissingEntry : SessionValidation
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

sealed interface SourceNameValidation {
    data class Valid(val name: String) : SourceNameValidation

    data object Blank : SourceNameValidation
}

internal fun normalizeSourceName(name: String): SourceNameValidation {
    val normalized = name.trim()
    return if (normalized.isBlank()) {
        SourceNameValidation.Blank
    } else {
        SourceNameValidation.Valid(normalized)
    }
}

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
