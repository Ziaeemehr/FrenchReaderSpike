package com.ziaee.frenchreader.ui.statistics

import com.ziaee.frenchreader.data.ReviewLogEntry
import com.ziaee.frenchreader.data.VocabEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class StatisticsAggregatesTest {
    private val utc = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 9, 21) // Monday

    private fun ms(d: LocalDate) = d.atTime(12, 0).toInstant(utc).toEpochMilli()
    private fun log(d: LocalDate, knew: Boolean, boxBefore: Int = 1) =
        ReviewLogEntry(entryId = 1, timestampMs = ms(d), knew = knew, boxBefore = boxBefore, boxAfter = boxBefore)
    private fun vocab(created: LocalDate, due: LocalDate, learned: Boolean = false, reviewed: Long? = 1L) =
        VocabEntry(word = "w", sentence = "s", textId = 0, dictionaryUrl = "",
            createdAtMs = ms(created), nextReviewAtMs = ms(due), learned = learned, lastReviewedAtMs = reviewed)

    @Test fun `lastDays returns n days oldest first`() {
        val days = lastDays(today, 3)
        assertEquals(listOf(today.minusDays(2), today.minusDays(1), today), days)
    }

    @Test fun `heatmap spans first Monday through today and counts per day`() {
        val logs = listOf(log(today, true), log(today, false), log(today.minusDays(1), true))
        val map = reviewHeatmap(logs, today, weeks = 2, zone = utc)
        assertEquals(today.minusWeeks(1), map.first().date)
        assertEquals(today, map.last().date)
        assertEquals(8, map.size) // Mon of last week .. Mon today
        assertEquals(2, map.last().count)
        assertEquals(1, map.first { it.date == today.minusDays(1) }.count)
    }

    @Test fun `forecast counts overdue on day zero and excludes learned`() {
        val entries = listOf(
            vocab(today, today.minusDays(5)),
            vocab(today, today),
            vocab(today, today.plusDays(2)),
            vocab(today, today.plusDays(40)),
            vocab(today, today, learned = true),
            vocab(today, today, reviewed = null)
        )
        val f = dueForecast(entries, today, days = 30, zone = utc)
        assertEquals(30, f.size)
        assertEquals(2, f[0])
        assertEquals(1, f[2])
        assertEquals(3, f.sum())
    }

    @Test fun `accuracy trend buckets by week`() {
        val logs = listOf(
            log(today, true), log(today, false),               // this week: 50%
            log(today.minusWeeks(1), true)                     // last week: 100%
        )
        val t = accuracyTrend(logs, today, weeks = 3, zone = utc)
        assertEquals(3, t.size)
        assertEquals(WeeklyAccuracy(today.minusWeeks(2), 0, 0), t[0])
        assertEquals(WeeklyAccuracy(today.minusWeeks(1), 100, 1), t[1])
        assertEquals(WeeklyAccuracy(today, 50, 2), t[2])
    }

    @Test fun `words added per week`() {
        val entries = listOf(vocab(today, today), vocab(today, today), vocab(today.minusWeeks(1), today))
        val w = wordsAddedPerWeek(entries, today, weeks = 2, zone = utc)
        assertEquals(listOf(1, 2), w.map { it.count }) // oldest week first
    }

    @Test fun `retention by box uses boxBefore`() {
        val logs = listOf(log(today, true, 2), log(today, false, 2), log(today, true, 5))
        val r = retentionByBox(logs)
        assertEquals(5, r.size)
        assertEquals(BoxRetention(1, 0, 0), r[0])
        assertEquals(BoxRetention(2, 50, 2), r[1])
        assertEquals(BoxRetention(5, 100, 1), r[4])
    }
}
