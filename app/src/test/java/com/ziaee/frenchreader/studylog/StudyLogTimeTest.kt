package com.ziaee.frenchreader.studylog

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyLogTimeTest {
    @Test
    fun `delay reaches the next local midnight across daylight saving`() {
        val zone = ZoneId.of("Europe/Paris")
        val now = Instant.parse("2026-03-28T23:30:00Z")
        val delay = millisUntilNextLocalMidnight(now, zone)
        val next = now.plusMillis(delay)

        assertEquals(0, next.atZone(zone).hour)
        assertEquals(0, next.atZone(zone).minute)
        assertTrue(delay > 0)
    }
}
