package com.ziaee.frenchreader.ui

import com.ziaee.frenchreader.data.VocabEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewQueueTest {
    private val day = 86_400_000L
    private val now = 10 * day + 5_000
    private val dayStart = 10 * day

    private fun e(id: Long, box: Int, due: Long, reviewed: Long? = 1L, created: Long = id, learned: Boolean = false) =
        VocabEntry(id = id, word = "w$id", sentence = "", textId = 0, dictionaryUrl = "", learned = learned,
            createdAtMs = created, leitnerBox = box, nextReviewAtMs = due, lastReviewedAtMs = reviewed)

    @Test fun `default queue is overdue then today then new capped`() {
        val entries = listOf(
            e(1, 2, dayStart + 100),            // due today
            e(2, 3, dayStart - day),            // overdue
            e(3, 1, now, reviewed = null, created = 30),  // new (newer)
            e(4, 1, now, reviewed = null, created = 20),  // new (older)
            e(5, 4, now + day),                 // not due
            e(6, 2, dayStart - 2 * day, learned = true)   // learned: excluded
        )
        assertEquals(listOf(2L, 1L, 4L), buildReviewQueue(entries, now, dayStart, newLimit = 1).map { it.id })
    }

    @Test fun `box queue keeps only that box's reviewed due cards`() {
        val entries = listOf(
            e(1, 2, dayStart - day), e(2, 2, dayStart + 10), e(3, 3, dayStart - day),
            e(4, 2, now + day), e(5, 2, now, reviewed = null)
        )
        assertEquals(listOf(1L, 2L), buildReviewQueue(entries, now, dayStart, newLimit = 10, box = 2).map { it.id })
    }

    @Test fun `bar fractions scale to the largest count`() {
        assertEquals(listOf(0.5f, 1f, 0f), boxBarFractions(listOf(5, 10, 0)))
        assertEquals(listOf(0f, 0f), boxBarFractions(listOf(0, 0)))
        assertEquals(emptyList<Float>(), boxBarFractions(emptyList()))
    }

    private fun ids(l: List<VocabEntry>) = l.map { it.id }

    @Test fun `undo restore removes requeued and puts current first`() {
        val o = (1L..4L).map { e(it, 1, 0) }
        val rq = e(9, 1, 0)
        val q = listOf(o[0], o[1], o[2], rq, o[3])
        val cur = e(7, 1, 0)
        val r = restoreQueueAfterUndo(q, cur, rq)
        assertEquals(listOf(7L, 1L, 2L, 3L, 4L), ids(r))
        assertEquals(true, r[0] === cur)
    }
    @Test fun `undo restore does not resurrect requeued that is current`() {
        val rq = e(9, 1, 0)
        assertEquals(emptyList<VocabEntry>(), restoreQueueAfterUndo(emptyList(), rq, rq))
    }
    @Test fun `undo restore without requeued only prepends current`() {
        val a = e(1, 1, 0); val cur = e(2, 1, 0)
        assertEquals(listOf(2L, 1L), ids(restoreQueueAfterUndo(listOf(a), cur, null)))
    }
    @Test fun `undo restore with no current only removes requeued`() {
        val a = e(1, 1, 0); val rq = e(9, 1, 0)
        assertEquals(listOf(1L), ids(restoreQueueAfterUndo(listOf(a, rq), null, rq)))
    }
}
