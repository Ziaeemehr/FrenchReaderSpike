package com.ziaee.frenchreader.data

import java.time.Instant
import java.time.ZoneId

enum class VocabAnswer { FORGOT, HARD, KNEW }

enum class VocabStatus { NEW, LEARNING, REVIEWING, LEARNED }

fun VocabEntry.status(): VocabStatus = when {
    learned -> VocabStatus.LEARNED
    lastReviewedAtMs == null -> VocabStatus.NEW
    leitnerBox in 1..2 -> VocabStatus.LEARNING
    leitnerBox in 3..4 -> VocabStatus.REVIEWING
    leitnerBox >= 5 -> VocabStatus.LEARNED
    else -> VocabStatus.LEARNING
}

object VocabSrs {
    const val DAY_MS = 86_400_000L
    val DEFAULT_INTERVAL_DAYS = listOf(1L, 2L, 4L, 8L, 16L)

    fun apply(
        entry: VocabEntry,
        answer: VocabAnswer,
        nowMs: Long,
        intervalDays: List<Long> = DEFAULT_INTERVAL_DAYS,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): VocabEntry {
        val newBox = when (answer) {
            VocabAnswer.FORGOT -> 1
            VocabAnswer.HARD -> entry.leitnerBox
            VocabAnswer.KNEW -> (entry.leitnerBox + 1).coerceAtMost(5)
        }
        return entry.copy(
            leitnerBox = newBox,
            nextReviewAtMs = dueAtMs(newBox, nowMs, intervalDays, zoneId),
            lastReviewedAtMs = nowMs,
            learned = false
        )
    }

    fun previewIntervalDays(
        entry: VocabEntry,
        answer: VocabAnswer,
        intervalDays: List<Long> = DEFAULT_INTERVAL_DAYS
    ): Long = intervalDays[(when (answer) {
        VocabAnswer.FORGOT -> 1
        VocabAnswer.HARD -> entry.leitnerBox
        VocabAnswer.KNEW -> (entry.leitnerBox + 1).coerceAtMost(5)
    } - 1).coerceIn(0, 4)]

    fun startOfDayMs(nowMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()

    fun dueAtMs(box: Int, nowMs: Long, intervals: List<Long>, zoneId: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(startOfDayMs(nowMs, zoneId)).atZone(zoneId)
            .plusDays(intervals[box.coerceIn(1, 5) - 1]).toInstant().toEpochMilli()
}
