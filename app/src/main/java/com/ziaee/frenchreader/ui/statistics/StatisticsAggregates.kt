package com.ziaee.frenchreader.ui.statistics

import com.ziaee.frenchreader.data.ReviewLogEntry
import com.ziaee.frenchreader.data.VocabEntry
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class HeatmapDay(val date: LocalDate, val count: Int)
data class WeeklyCount(val weekStart: LocalDate, val count: Int)
data class WeeklyAccuracy(val weekStart: LocalDate, val percent: Int, val answers: Int)
data class BoxRetention(val box: Int, val percent: Int, val answers: Int)

private fun LocalDate.weekStart(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
private fun Long.toLocalDate(zone: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

internal fun lastDays(today: LocalDate, n: Int): List<LocalDate> =
    (n - 1 downTo 0).map { today.minusDays(it.toLong()) }

internal fun reviewHeatmap(
    logs: List<ReviewLogEntry>, today: LocalDate, weeks: Int = 12, zone: ZoneId = ZoneId.systemDefault()
): List<HeatmapDay> {
    val start = today.weekStart().minusWeeks((weeks - 1).toLong())
    val counts = logs.groupingBy { it.timestampMs.toLocalDate(zone) }.eachCount()
    val length = ChronoUnit.DAYS.between(start, today).toInt() + 1
    return (0 until length).map { i ->
        val d = start.plusDays(i.toLong())
        HeatmapDay(d, counts[d] ?: 0)
    }
}

internal fun dueForecast(
    entries: List<VocabEntry>, today: LocalDate, days: Int = 30, zone: ZoneId = ZoneId.systemDefault()
): List<Int> {
    val buckets = IntArray(days)
    entries.filter { !it.learned && it.lastReviewedAtMs != null }.forEach { e ->
        val offset = ChronoUnit.DAYS.between(today, e.nextReviewAtMs.toLocalDate(zone)).toInt().coerceAtLeast(0)
        if (offset < days) buckets[offset]++
    }
    return buckets.toList()
}

private fun weekStarts(today: LocalDate, weeks: Int): List<LocalDate> =
    (weeks - 1 downTo 0).map { today.weekStart().minusWeeks(it.toLong()) }

internal fun accuracyTrend(
    logs: List<ReviewLogEntry>, today: LocalDate, weeks: Int = 8, zone: ZoneId = ZoneId.systemDefault()
): List<WeeklyAccuracy> {
    val byWeek = logs.groupBy { it.timestampMs.toLocalDate(zone).weekStart() }
    return weekStarts(today, weeks).map { ws ->
        val week = byWeek[ws].orEmpty()
        WeeklyAccuracy(ws, computeAccuracyPercent(week.count { it.knew }, week.size), week.size)
    }
}

internal fun wordsAddedPerWeek(
    entries: List<VocabEntry>, today: LocalDate, weeks: Int = 8, zone: ZoneId = ZoneId.systemDefault()
): List<WeeklyCount> {
    val byWeek = entries.groupingBy { it.createdAtMs.toLocalDate(zone).weekStart() }.eachCount()
    return weekStarts(today, weeks).map { WeeklyCount(it, byWeek[it] ?: 0) }
}

internal fun retentionByBox(logs: List<ReviewLogEntry>): List<BoxRetention> {
    val byBox = logs.groupBy { it.boxBefore }
    return (1..LEITNER_BOX_COUNT).map { box ->
        val rows = byBox[box].orEmpty()
        BoxRetention(box, computeAccuracyPercent(rows.count { it.knew }, rows.size), rows.size)
    }
}
