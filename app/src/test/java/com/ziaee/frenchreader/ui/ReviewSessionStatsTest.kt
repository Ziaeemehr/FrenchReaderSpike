package com.ziaee.frenchreader.ui

import com.ziaee.frenchreader.data.VocabAnswer
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewSessionStatsTest {
    @Test fun `forgot from box 3 counts return to box one`() {
        val s = ReviewSessionStats().after(VocabAnswer.FORGOT, 3, 1)
        assertEquals(ReviewSessionStats(1, 0, 0, 1), s)
    }
    @Test fun `forgot from box 1 does not count return`() {
        assertEquals(ReviewSessionStats(1, 0, 0, 0), ReviewSessionStats().after(VocabAnswer.FORGOT, 1, 1))
    }
    @Test fun `hard counts correct without moving forward`() {
        assertEquals(ReviewSessionStats(1, 1, 0, 0), ReviewSessionStats().after(VocabAnswer.HARD, 2, 2))
    }
    @Test fun `knew moving box counts forward`() {
        assertEquals(ReviewSessionStats(1, 1, 1, 0), ReviewSessionStats().after(VocabAnswer.KNEW, 2, 3))
    }
    @Test fun `study time is split at local midnight across DST`() {
        val zone = java.time.ZoneId.of("Europe/Paris")
        val start = java.time.ZonedDateTime.parse("2026-03-28T23:30:00+01:00[Europe/Paris]").toInstant().toEpochMilli()
        val end = java.time.ZonedDateTime.parse("2026-03-30T00:30:00+02:00[Europe/Paris]").toInstant().toEpochMilli()
        assertEquals(
            mapOf("2026-03-28" to 1_800_000L, "2026-03-29" to 82_800_000L, "2026-03-30" to 1_800_000L),
            reviewStudyTimeByDate(start, end, zone)
        )
    }

    @Test fun `backward clock does not record negative study time`() {
        assertEquals(emptyMap<String, Long>(), reviewStudyTimeByDate(100L, 99L))
    }
    @Test fun `study timer excludes pauses and records each interval only once`() {
        val timer = ReviewStudyTimer(java.time.ZoneOffset.UTC)
        timer.resume(1_000L)
        timer.resume(2_000L) // duplicate resume must not lose the first second
        assertEquals(mapOf("1970-01-01" to 3_000L), timer.pause(4_000L))
        assertEquals(emptyMap<String, Long>(), timer.pause(8_000L))
        timer.resume(10_000L)
        assertEquals(mapOf("1970-01-01" to 2_000L), timer.pause(12_000L))
    }

    @Test fun `study timer splits only active time across midnight`() {
        val timer = ReviewStudyTimer(java.time.ZoneOffset.UTC)
        timer.resume(86_399_000L)
        assertEquals(mapOf("1970-01-01" to 1_000L, "1970-01-02" to 2_000L), timer.pause(86_402_000L))
        timer.resume(172_801_000L)
        assertEquals(mapOf("1970-01-03" to 1_000L), timer.pause(172_802_000L))
    }

}
