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

private val LESSON_TAG_REGEX = Regex("(?:^|\\s*—\\s*)Leçon (\\d+(?:\\.\\d+)?)$")

/** Anki-imported decks (see Gram_diag_B1 / Communication progressive imports) stash a
 * "— Leçon N" suffix in [VocabEntry.meaning] since there's no dedicated column for it.
 * This pulls just the number back out for a small corner badge. */
fun VocabEntry.lessonNumber(): String? = meaning?.let { LESSON_TAG_REGEX.find(it)?.groupValues?.get(1) }

/** [VocabEntry.meaning] with the "— Leçon N" tag (see [lessonNumber]) stripped, so the
 * meaning shown on a card doesn't carry that bookkeeping suffix. Null/blank if nothing's left. */
fun VocabEntry.displayMeaning(): String? = meaning?.replace(LESSON_TAG_REGEX, "")?.trim()?.takeIf { it.isNotBlank() }

private val ARABIC_SCRIPT = Regex(
    "[\\u0600-\\u06FF\\u0750-\\u077F\\uFB50-\\uFDFF\\uFE70-\\uFEFF]"
)
private val LATIN_LETTER = Regex("[A-Za-z\\u00C0-\\u024F]")

fun isLikelyFrench(text: String): Boolean =
    !ARABIC_SCRIPT.containsMatchIn(text) && LATIN_LETTER.containsMatchIn(text)

object VocabSrs {
    const val DAY_MS = 86_400_000L
    val DEFAULT_INTERVAL_DAYS = listOf(1L, 2L, 4L, 8L, 16L)

    /** A card still being learned: new, or back in box 1 and already answered today. Its first
     * spaced review is tomorrow, so Good keeps it in box 1 instead of skipping to box 2. */
    fun isLearningStep(entry: VocabEntry, nowMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): Boolean =
        entry.leitnerBox <= 1 &&
            (entry.lastReviewedAtMs == null || entry.lastReviewedAtMs >= startOfDayMs(nowMs, zoneId))

    private fun nextBox(entry: VocabEntry, answer: VocabAnswer, nowMs: Long, zoneId: ZoneId): Int = when (answer) {
        VocabAnswer.FORGOT -> 1
        VocabAnswer.HARD -> entry.leitnerBox
        VocabAnswer.KNEW ->
            if (isLearningStep(entry, nowMs, zoneId)) 1 else (entry.leitnerBox + 1).coerceAtMost(5)
    }

    fun apply(
        entry: VocabEntry,
        answer: VocabAnswer,
        nowMs: Long,
        intervalDays: List<Long> = DEFAULT_INTERVAL_DAYS,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): VocabEntry {
        val newBox = nextBox(entry, answer, nowMs, zoneId)
        return entry.copy(
            leitnerBox = newBox,
            nextReviewAtMs = dueAtMs(newBox, nowMs, intervalDays, zoneId),
            lastReviewedAtMs = nowMs,
            learned = newBox == 5
        )
    }

    fun previewIntervalDays(
        entry: VocabEntry,
        answer: VocabAnswer,
        intervalDays: List<Long> = DEFAULT_INTERVAL_DAYS,
        nowMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long = intervalDays[(nextBox(entry, answer, nowMs, zoneId) - 1).coerceIn(0, 4)]

    fun startOfDayMs(nowMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()

    fun dueAtMs(box: Int, nowMs: Long, intervals: List<Long>, zoneId: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(startOfDayMs(nowMs, zoneId)).atZone(zoneId)
            .plusDays(intervals[box.coerceIn(1, 5) - 1]).toInstant().toEpochMilli()
}
