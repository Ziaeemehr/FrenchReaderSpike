package com.ziaee.frenchreader.ui.statistics

import com.ziaee.frenchreader.data.TextDocument
import com.ziaee.frenchreader.data.VocabEntry
import com.ziaee.frenchreader.text.TextChunker
import com.ziaee.frenchreader.text.BlockType
import com.ziaee.frenchreader.text.MarkdownParser
import java.time.LocalDate

internal const val LEITNER_BOX_COUNT = 5

/** One day's listening time, always present for all 7 days in the chart
 * even when [listeningMs] is 0 -- so the chart never has a missing bar. */
data class DailyListening(val date: LocalDate, val listeningMs: Long)

data class StatisticsUiState(
    val streakDays: Int = 0,
    val weeklyListening: List<DailyListening> = emptyList(),
    val reviewedToday: Int = 0,
    val reviewedThisWeek: Int = 0,
    val accuracyPercent: Int = 0,
    val leitnerBoxCounts: Map<Int, Int> = emptyMap(),
    val textsSaved: Int = 0,
    val textsCompleted: Int = 0,
    val wordsSaved: Int = 0
)

/** A text is "completed" once its saved reading position has reached the
 * last chunk at least once. */
internal fun isTextCompleted(doc: TextDocument, body: String): Boolean {
    val chunks = TextChunker.chunk(body)
    val lastSpokenIndex = chunks.indexOfLast { MarkdownParser.parse(it).type != BlockType.IMAGE }
    return lastSpokenIndex >= 0 && doc.lastChunkIndex >= lastSpokenIndex
}

internal fun leitnerBoxCounts(entries: List<VocabEntry>): Map<Int, Int> {
    val counts = entries.groupingBy { it.leitnerBox }.eachCount()
    return (1..LEITNER_BOX_COUNT).associateWith { box -> counts[box] ?: 0 }
}

internal fun computeAccuracyPercent(knewCount: Int, totalCount: Int): Int =
    if (totalCount == 0) 0 else ((knewCount * 100L) / totalCount).toInt()

internal fun last7Days(today: LocalDate): List<LocalDate> =
    (6 downTo 0).map { today.minusDays(it.toLong()) }

/** "Any activity" streak: a day counts if it's in [activeDates] (listening
 * OR a vocab review happened that day). Falls back to checking yesterday
 * if today has no activity logged yet, so the streak doesn't drop to zero
 * first thing in the morning before the user has done anything today. */
internal fun computeStreak(activeDates: Set<LocalDate>, today: LocalDate): Int {
    val start = if (today in activeDates) today else today.minusDays(1)
    if (start !in activeDates) return 0
    var streak = 0
    var day = start
    while (day in activeDates) {
        streak++
        day = day.minusDays(1)
    }
    return streak
}

/** Shared by Statistics and Home so both derive the exact same "any
 * activity" date set from `review_log` + `activity_log` without duplicating
 * the query/union/parsing logic. The two DAO calls are passed as suspend
 * lambdas so this stays a plain unit-testable function. */
internal suspend fun loadActiveDates(
    reviewLogDates: suspend () -> List<String>,
    activityLogDates: suspend () -> List<String>
): Set<LocalDate> =
    (reviewLogDates() + activityLogDates()).toSet().map { LocalDate.parse(it) }.toSet()

internal fun composeStatisticsState(
    texts: List<TextDocument>,
    bodyByTextId: Map<Long, String> = emptyMap(),
    vocabEntries: List<VocabEntry>,
    reviewedToday: Int,
    reviewedThisWeek: Int,
    knewCount: Int,
    totalReviewCount: Int,
    weeklyListening: List<DailyListening>,
    activeDates: Set<LocalDate>,
    today: LocalDate
): StatisticsUiState = StatisticsUiState(
    streakDays = computeStreak(activeDates, today),
    weeklyListening = weeklyListening,
    reviewedToday = reviewedToday,
    reviewedThisWeek = reviewedThisWeek,
    accuracyPercent = computeAccuracyPercent(knewCount, totalReviewCount),
    leitnerBoxCounts = leitnerBoxCounts(vocabEntries),
    textsSaved = texts.size,
    textsCompleted = texts.count { isTextCompleted(it, bodyByTextId[it.id].orEmpty()) },
    wordsSaved = vocabEntries.size
)
