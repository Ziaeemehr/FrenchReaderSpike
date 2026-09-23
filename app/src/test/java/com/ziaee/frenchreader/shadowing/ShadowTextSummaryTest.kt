package com.ziaee.frenchreader.shadowing

import com.ziaee.frenchreader.data.ShadowAttempt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShadowTextSummaryTest {
    private fun a(chunk: Int, sentence: Int, matched: Int, total: Int, pace: Float?, ts: Long) = ShadowAttempt(
        textId = 1, chunkIndex = chunk, sentenceIndex = sentence, matched = matched, total = total,
        paceRatio = pace, engine = "vosk", timestampMs = ts
    )

    @Test fun emptyIsNull() = assertNull(shadowTextSummary(emptyList()))

    @Test fun latestAttemptPerSentenceWins() {
        val s = shadowTextSummary(listOf(a(0, 0, 1, 4, 2.0f, 100), a(0, 0, 4, 4, 1.0f, 200)))!!
        assertEquals(1, s.sentences)
        assertEquals(100, s.accuracyPercent)
        assertEquals(1.0f, s.averagePaceRatio!!, 0.001f)
        assertEquals(100, s.goodPacePercent)
        assertEquals(emptyList<WeakSentence>(), s.weakest)
    }

    @Test fun accuracyIsWordWeighted_andPaceShareCounted() {
        val s = shadowTextSummary(listOf(
            a(0, 0, 3, 4, 1.0f, 1), a(0, 1, 1, 2, 1.6f, 2), a(1, 0, 5, 5, null, 3)
        ))!!
        assertEquals(3, s.sentences)
        assertEquals(82, s.accuracyPercent) // 9 / 11
        assertEquals(1.3f, s.averagePaceRatio!!, 0.001f) // (1.0 + 1.6) / 2, nulls skipped
        assertEquals(50, s.goodPacePercent)
    }

    @Test fun weakestAreLowestFirst_perfectExcluded_maxThree() {
        val s = shadowTextSummary(listOf(
            a(0, 0, 3, 4, null, 1), a(0, 1, 1, 4, null, 2), a(0, 2, 2, 4, null, 3),
            a(0, 3, 0, 4, null, 4), a(0, 4, 4, 4, null, 5)
        ))!!
        assertEquals(listOf(SentenceRef(0, 3), SentenceRef(0, 1), SentenceRef(0, 2)), s.weakest.map { it.ref })
        assertEquals(listOf(0, 25, 50), s.weakest.map { it.accuracyPercent })
    }

    @Test fun noPaceData_paceFieldsNull() {
        val s = shadowTextSummary(listOf(a(0, 0, 2, 4, null, 1)))!!
        assertNull(s.averagePaceRatio)
        assertNull(s.goodPacePercent)
    }
}
