package com.ziaee.frenchreader.ui.statistics

import com.ziaee.frenchreader.data.DailyActivityRow
import com.ziaee.frenchreader.studylog.weekStart
import java.time.DayOfWeek
import java.time.LocalDate

internal fun computeActivityStreak(
    dailyMinutes: Map<LocalDate, Double>,
    legacyActiveDates: Set<LocalDate>,
    cutover: LocalDate,
    goalMinutes: Int,
    today: LocalDate
): Int {
    require(goalMinutes > 0)
    fun active(day: LocalDate) = if (day < cutover) day in legacyActiveDates
        else (dailyMinutes[day] ?: 0.0) >= goalMinutes
    var day = if (active(today)) today else today.minusDays(1)
    var streak = 0
    while (active(day)) {
        streak++
        day = day.minusDays(1)
    }
    return streak
}

internal fun dailyActivityMinutes(rows: List<DailyActivityRow>): Map<LocalDate, Double> =
    rows.groupBy { LocalDate.parse(it.date) }.mapValues { (_, rows) -> rows.sumOf { it.activityMs } / 60_000.0 }

internal fun activityWeekMinutes(
    dailyMinutes: Map<LocalDate, Double>,
    today: LocalDate,
    firstDayOfWeek: DayOfWeek
): Double = (weekStart(today.toEpochDay(), firstDayOfWeek)..today.toEpochDay()).sumOf {
    dailyMinutes[LocalDate.ofEpochDay(it)] ?: 0.0
}
