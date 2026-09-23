package com.ziaee.frenchreader.ui

import com.ziaee.frenchreader.data.VocabEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
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

    @Test fun `reviewable count includes due reviews and caps new cards`() {
        val entries = listOf(
            e(1, 2, now),
            e(2, 3, now + day),
            e(3, 1, now, reviewed = null),
            e(4, 1, now, reviewed = null),
            e(5, 1, now, reviewed = null),
            e(6, 2, now, learned = true)
        )

        assertEquals(3, reviewableCount(entries, now, remainingNew = 2))
        assertEquals(1, reviewableCount(entries, now, remainingNew = 0))
    }

    @Test fun `learned queue resumes after cursor in id order`() {
        val entries = listOf(e(8, 5, 0, learned = true), e(2, 5, 0, learned = true), e(5, 5, 0, learned = true))

        val result = buildLearnedReviewQueue(entries, cursor = 2, scope = VOCAB_SCOPE_ALL)

        assertEquals(listOf(5L, 8L), result.cards.map { it.id })
        assertEquals(3, result.total)
        assertEquals(1, result.reviewed)
        assertEquals(false, result.resetCursor)
    }

    @Test fun `learned queue wraps when persisted cursor is complete`() {
        val entries = listOf(e(2, 5, 0, learned = true), e(5, 5, 0, learned = true))

        val result = buildLearnedReviewQueue(entries, cursor = 5, scope = VOCAB_SCOPE_ALL)

        assertEquals(listOf(2L, 5L), result.cards.map { it.id })
        assertEquals(0, result.reviewed)
        assertEquals(true, result.resetCursor)
    }

    @Test fun `learned queue filters scope and excludes active cards`() {
        val entries = listOf(
            e(1, 5, 0, learned = true).copy(listId = 7),
            e(2, 5, 0, learned = true).copy(listId = null),
            e(3, 4, 0, learned = false).copy(listId = 7)
        )

        assertEquals(listOf(1L), buildLearnedReviewQueue(entries, 0, 7).cards.map { it.id })
        assertEquals(listOf(2L), buildLearnedReviewQueue(entries, 0, VOCAB_SCOPE_UNFILED).cards.map { it.id })
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
        assertNotSame(cur, r[0])
        assertSame(o[0], r[1]); assertSame(o[1], r[2]); assertSame(o[2], r[3]); assertSame(o[3], r[4])
    }
    @Test fun `undo restore does not resurrect requeued that is current`() {
        val rq = e(9, 1, 0)
        assertEquals(emptyList<VocabEntry>(), restoreQueueAfterUndo(emptyList(), rq, rq))
    }
    @Test fun `undo restore without requeued only prepends current`() {
        val a = e(1, 1, 0); val cur = e(2, 1, 0)
        val r = restoreQueueAfterUndo(listOf(a), cur, null)
        assertEquals(listOf(2L, 1L), ids(r)); assertNotSame(cur, r.first()); assertEquals(cur, r.first()); assertSame(a, r[1])
    }
    @Test fun `undo restore with no current only removes requeued`() {
        val a = e(1, 1, 0); val rq = e(9, 1, 0)
        assertEquals(listOf(1L), ids(restoreQueueAfterUndo(listOf(a, rq), null, rq)))
    }

    @Test fun `reveal state accepts a structurally equal replacement instance after undo`() {
        val beforeUndo = e(1, 1, 0)
        val restoredAfterUndo = beforeUndo.copy()
        val state = newRevealedEntryState()

        state.value = beforeUndo
        state.value = restoredAfterUndo

        assertSame(restoredAfterUndo, state.value)
    }
}
