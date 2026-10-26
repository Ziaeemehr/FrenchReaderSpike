package com.ziaee.frenchreader.ui.activity

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityTimerTest {
    private var elapsed = 0L
    private var wall = Instant.parse("2026-10-26T12:00:00Z").toEpochMilli()
    private fun advance(ms: Long) { elapsed += ms; wall += ms }
    private fun timer(reading: Boolean) = ActivityTimer(reading, { elapsed }, { wall }, { ZoneId.of("UTC") })

    @Test fun `reading starts after interaction and stops after sixty seconds`() {
        val timer = timer(true)
        timer.setEnabled(true)
        advance(20_000)
        assertEquals(emptyMap<String, Long>(), timer.drain())
        timer.interact()
        advance(90_000)
        assertEquals(mapOf("2026-10-26" to 60_000L), timer.drain())
        advance(10_000)
        timer.interact()
        advance(5_000)
        assertEquals(mapOf("2026-10-26" to 5_000L), timer.drain())
    }

    @Test fun `interaction extends reading and TTS or pause excludes time`() {
        val timer = timer(true)
        timer.setEnabled(true)
        timer.interact()
        advance(50_000)
        timer.interact()
        advance(20_000)
        timer.setEnabled(false)
        advance(20_000)
        timer.setEnabled(true)
        advance(30_000)
        assertEquals(mapOf("2026-10-26" to 90_000L), timer.drain())
    }

    @Test fun `watching counts only enabled intervals and draining does not double count`() {
        val timer = timer(false)
        timer.setEnabled(true)
        advance(15_000)
        assertEquals(mapOf("2026-10-26" to 15_000L), timer.drain())
        advance(2_000)
        timer.setEnabled(false)
        advance(40_000)
        assertEquals(mapOf("2026-10-26" to 2_000L), timer.drain())
        assertEquals(emptyMap<String, Long>(), timer.drain())
    }

    @Test fun `elapsed time is split at local midnight`() {
        wall = Instant.parse("2026-10-26T23:59:50Z").toEpochMilli()
        val timer = timer(false)
        timer.setEnabled(true)
        advance(25_000)
        assertEquals(mapOf("2026-10-26" to 10_000L, "2026-10-27" to 15_000L), timer.drain())
    }
    @Test fun `screen must be resumed and eligible and pause flushes immediately`() {
        val saved = mutableMapOf<String, Long>()
        val screen = ScreenActivityTimer(timer(false)) { rows ->
            rows.forEach { (date, ms) -> saved[date] = saved.getOrDefault(date, 0) + ms }
        }
        screen.setEligible(true)
        advance(10_000)
        screen.setResumed(true)
        advance(5_000)
        screen.setResumed(false)
        assertEquals(mapOf("2026-10-26" to 5_000L), saved)
        advance(20_000)
        screen.setResumed(true)
        screen.setEligible(false)
        advance(20_000)
        screen.flush()
        assertEquals(mapOf("2026-10-26" to 5_000L), saved)
    }

    @Test fun `midnight uses the local zone rather than UTC`() {
        wall = Instant.parse("2026-10-26T22:59:50Z").toEpochMilli()
        val timer = ActivityTimer(false, { elapsed }, { wall }, { ZoneId.of("Europe/Paris") })
        timer.setEnabled(true)
        advance(25_000)
        assertEquals(mapOf("2026-10-26" to 10_000L, "2026-10-27" to 15_000L), timer.drain())
    }
}
