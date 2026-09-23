package com.ziaee.frenchreader.ui

import com.ziaee.frenchreader.data.VocabEntry

internal fun reviewableCount(entries: List<VocabEntry>, nowMs: Long, remainingNew: Int): Int =
    entries.count { !it.learned && it.lastReviewedAtMs != null && it.nextReviewAtMs <= nowMs } +
        entries.count { !it.learned && it.lastReviewedAtMs == null }.coerceAtMost(remainingNew.coerceAtLeast(0))

internal fun buildReviewQueue(
    entries: List<VocabEntry>, nowMs: Long, dayStartMs: Long, newLimit: Int, box: Int? = null
): List<VocabEntry> {
    val active = entries.filter { !it.learned }
    val scoped = if (box == null) active else active.filter { it.leitnerBox == box }
    val overdue = scoped.filter { it.lastReviewedAtMs != null && it.nextReviewAtMs < dayStartMs }.sortedBy { it.nextReviewAtMs }
    val today = scoped.filter { it.lastReviewedAtMs != null && it.nextReviewAtMs in dayStartMs..nowMs }.sortedBy { it.nextReviewAtMs }
    val fresh = if (box != null) emptyList()
    else active.filter { it.lastReviewedAtMs == null }.sortedBy { it.createdAtMs }.take(newLimit)
    return overdue + today + fresh
}

internal data class LearnedReviewQueue(
    val cards: List<VocabEntry>, val total: Int, val reviewed: Int, val resetCursor: Boolean
)

internal fun buildLearnedReviewQueue(
    entries: List<VocabEntry>, cursor: Long, scope: Long
): LearnedReviewQueue {
    val learned = entries.asSequence().filter { it.learned }.filter {
        when (scope) {
            VOCAB_SCOPE_ALL -> true
            VOCAB_SCOPE_UNFILED -> it.listId == null
            else -> it.listId == scope
        }
    }.sortedBy { it.id }.toList()
    val remaining = learned.filter { it.id > cursor }
    val reset = cursor > 0 && learned.isNotEmpty() && remaining.isEmpty()
    return LearnedReviewQueue(
        cards = if (reset) learned else remaining,
        total = learned.size,
        reviewed = if (reset) 0 else learned.count { it.id <= cursor },
        resetCursor = reset
    )
}

internal fun boxBarFractions(counts: List<Int>): List<Float> {
    val max = maxOf(counts.maxOrNull() ?: 0, 1)
    return counts.map { it.toFloat() / max }
}

internal fun restoreQueueAfterUndo(queue: List<VocabEntry>, current: VocabEntry?, requeued: VocabEntry?): List<VocabEntry> {
    val rest = queue.filter { it !== requeued }
    return if (current != null && current !== requeued) listOf(current.copy()) + rest else rest
}
