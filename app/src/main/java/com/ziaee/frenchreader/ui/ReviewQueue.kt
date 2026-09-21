package com.ziaee.frenchreader.ui

import com.ziaee.frenchreader.data.VocabEntry

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

internal fun boxBarFractions(counts: List<Int>): List<Float> {
    val max = maxOf(counts.maxOrNull() ?: 0, 1)
    return counts.map { it.toFloat() / max }
}

internal fun restoreQueueAfterUndo(queue: List<VocabEntry>, current: VocabEntry?, requeued: VocabEntry?): List<VocabEntry> {
    val rest = queue.filter { it !== requeued }
    return if (current != null && current !== requeued) listOf(current.copy()) + rest else rest
}
