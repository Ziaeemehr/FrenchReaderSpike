package com.ziaee.frenchreader.data

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
    const val FORGOT_DELAY_MS = 10 * 60_000L
    val BOX_INTERVAL_DAYS = mapOf(1 to 1L, 2 to 3L, 3 to 7L, 4 to 16L, 5 to 30L)

    fun apply(entry: VocabEntry, answer: VocabAnswer, nowMs: Long): VocabEntry {
        val newBox = when (answer) {
            VocabAnswer.FORGOT -> 1
            VocabAnswer.HARD -> entry.leitnerBox
            VocabAnswer.KNEW -> (entry.leitnerBox + 1).coerceAtMost(5)
        }
        return entry.copy(
            leitnerBox = newBox,
            nextReviewAtMs = nowMs + previewIntervalMs(entry, answer),
            lastReviewedAtMs = nowMs,
            learned = answer == VocabAnswer.KNEW && newBox >= 5
        )
    }

    fun previewIntervalMs(entry: VocabEntry, answer: VocabAnswer): Long = when (answer) {
        VocabAnswer.FORGOT -> FORGOT_DELAY_MS
        VocabAnswer.HARD -> intervalMs(entry.leitnerBox) / 2
        VocabAnswer.KNEW -> intervalMs((entry.leitnerBox + 1).coerceAtMost(5))
    }

    private fun intervalMs(box: Int) = (BOX_INTERVAL_DAYS[box] ?: 30L) * DAY_MS
}
