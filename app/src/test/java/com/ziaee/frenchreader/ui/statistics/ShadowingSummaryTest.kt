package com.ziaee.frenchreader.ui.statistics

import com.ziaee.frenchreader.data.ShadowAttempt
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ShadowingSummaryTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 9, 23)
    private fun at(d: LocalDate, matched: Int, total: Int) = ShadowAttempt(
        textId = 1, chunkIndex = 0, sentenceIndex = 0, matched = matched, total = total, engine = "vosk",
        timestampMs = d.atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli()
    )

    @Test fun averagesByWordsAndBucketsByDay() {
        val s = shadowingSummary(
            totalCount = 12,
            recent = listOf(at(today, 3, 4), at(today, 1, 4), at(today.minusDays(6), 5, 5)),
            today = today, zone = zone
        )
        assertEquals(12, s.sentencesPracticed)
        assertEquals(69, s.averageAccuracyPercent) // (3+1+5)/(4+4+5) = 9/13
        assertEquals(listOf(100, 0, 0, 0, 0, 0, 50), s.last7DaysPercent)
    }

    @Test fun emptyIsZeros() {
        val s = shadowingSummary(0, emptyList(), today, zone)
        assertEquals(0, s.averageAccuracyPercent)
        assertEquals(List(7) { 0 }, s.last7DaysPercent)
    }
}
