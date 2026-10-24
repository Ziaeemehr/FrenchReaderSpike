package com.ziaee.frenchreader.studylog

import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
    @Test
    fun `default date flow follows a changed system timezone`() = runBlocking {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val flow = localEpochDayFlow(now = { Instant.parse("2026-10-24T23:30:00Z") })
            val before = flow.first()
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            assertEquals(before + 1, flow.first())
        } finally {
            TimeZone.setDefault(previous)
        }
    }

}
