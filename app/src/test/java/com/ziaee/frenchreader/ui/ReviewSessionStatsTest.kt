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
}
