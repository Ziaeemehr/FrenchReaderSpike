package com.ziaee.frenchreader.watch

import org.junit.Assert.*
import org.junit.Test

class PlaybackFreezeTest {
    @Test fun explicitResumeClearsTheFrozenCueAndGraceWindow() {
        val frozen = PlaybackFreeze(cueTimeMs = 4_000, frozenAtMs = 10_000)
        assertTrue(frozen.guardsPlaying(false, 10_100))
        val resumed = frozen.resume()
        assertNull(resumed.cueTimeMs)
        assertEquals(0L, resumed.frozenAtMs)
        assertFalse(resumed.guardsPlaying(false, 10_100))
        assertFalse(resumed.guardsPlaying(true, 10_100))
    }

    @Test fun graceGuardsOnlyAnActiveFreeze() {
        assertFalse(PlaybackFreeze(null, 10_000).guardsPlaying(false, 10_100))
        assertTrue(PlaybackFreeze(4_000, 10_000).guardsPlaying(false, 11_499))
        assertFalse(PlaybackFreeze(4_000, 10_000).guardsPlaying(false, 11_500))
        assertTrue(PlaybackFreeze(4_000, 10_000).guardsPlaying(true, 12_000))
    }
    @Test fun pausedPlayerSeekClearsCaptionAndGraceInEitherDirection() {
        val frozen = PlaybackFreeze(4_000, 10_000)
        for (time in listOf(1_999L, 6_001L)) {
            val sought = frozen.onTime(4_000, time, 2, false)
            assertNull(sought.cueTimeMs)
            assertFalse(sought.guardsPlaying(false, 10_100))
        }
        assertEquals(frozen, frozen.onTime(4_000, 6_000, 2, false))
    }

    @Test fun bufferingAfterAPlayingTimeJumpClearsFreeze() {
        val frozen = PlaybackFreeze(4_000, 10_000)
        val jumped = frozen.onTime(4_000, 9_000, 1, false)
        assertEquals(4_000L, jumped.cueTimeMs)
        assertNull(jumped.onPlayerState(3, false).cueTimeMs)
        assertEquals(frozen, frozen.onPlayerState(3, false))
        assertNull(frozen.onTime(4_000, 9_000, 3, false).cueTimeMs)
    }

    @Test fun ordinaryTicksAndShadowingSeeksKeepFreeze() {
        val frozen = PlaybackFreeze(4_000, 10_000)
        assertEquals(frozen, frozen.onTime(4_000, 4_200, 2, false))
        assertEquals(frozen, frozen.onTime(4_000, 9_000, 2, true))
        val tick = frozen.onTime(4_000, 9_000, 1, false).onTime(9_000, 9_200, 1, false)
        assertEquals(frozen, tick.onPlayerState(3, false))
        assertEquals(frozen, frozen.onTime(4_000, 9_000, 1, true).onPlayerState(3, true))
    }

    @Test fun smallScrubWhilePausedClearsFreezeAfterGrace() {
        val frozen = PlaybackFreeze(4_000, frozenAtMs = 10_000)
        assertEquals(frozen, frozen.onTime(4_000, 4_900, 2, false, nowMs = 10_500))
        assertNull(frozen.onTime(4_000, 4_900, 2, false, nowMs = 12_000).cueTimeMs)
        assertEquals(frozen, frozen.onTime(4_000, 4_100, 2, false, nowMs = 12_000))
    }
}
