package com.ziaee.frenchreader.watch

/** Explicit resume removes both the frozen line and the late-playing-event grace window. */
data class PlaybackFreeze(val cueTimeMs: Long? = null, val frozenAtMs: Long = 0L, private val timeJumped: Boolean = false) {
    fun resume() = PlaybackFreeze()
    fun onTime(previousMs: Long, timeMs: Long, playerState: Int, playingUnit: Boolean, nowMs: Long = frozenAtMs): PlaybackFreeze {
        if (cueTimeMs == null || playingUnit) return this
        // Once the late pause has settled, a paused player's time only moves when the user scrubs it.
        if (playerState == 2 && nowMs - frozenAtMs >= 1_500L && kotlin.math.abs(timeMs - previousMs) > 300L) return resume()
        val jumped = kotlin.math.abs(timeMs - previousMs) > 2_000L
        return if (jumped && playerState != 1) resume() else copy(timeJumped = jumped)
    }
    fun onPlayerState(state: Int, playingUnit: Boolean): PlaybackFreeze =
        if (state == 3 && timeJumped && !playingUnit) resume() else this

    fun guardsPlaying(gestureActive: Boolean, nowMs: Long): Boolean =
        cueTimeMs != null && (gestureActive || nowMs - frozenAtMs < 1_500L)
}
