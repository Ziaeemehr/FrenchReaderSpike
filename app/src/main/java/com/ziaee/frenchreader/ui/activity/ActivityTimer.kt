package com.ziaee.frenchreader.ui.activity

import java.time.Instant
import java.time.ZoneId

/** Accounts elapsed time before each state change; inactivity never extends a reading session. */
internal class ActivityTimer(
    private val requiresInteraction: Boolean,
    private val elapsedMs: () -> Long,
    private val wallMs: () -> Long,
    private val zone: () -> ZoneId = ZoneId::systemDefault
) {
    private var lastElapsed = elapsedMs()
    private var lastWall = wallMs()
    private var lastInteraction: Long? = null
    private var enabled = false
    private val pending = mutableMapOf<String, Long>()

    fun setEnabled(value: Boolean) {
        account()
        enabled = value
    }

    fun interact() {
        account()
        lastInteraction = elapsedMs()
    }

    fun drain(): Map<String, Long> {
        account()
        return pending.toMap().also { pending.clear() }
    }

    private fun account() {
        val now = elapsedMs()
        val end = if (requiresInteraction) minOf(now, lastInteraction?.plus(60_000) ?: lastElapsed) else now
        val duration = if (enabled) (end - lastElapsed).coerceAtLeast(0) else 0
        var startWall = lastWall
        val endWall = startWall + duration
        val zoneId = zone()
        while (startWall < endWall) {
            val day = Instant.ofEpochMilli(startWall).atZone(zoneId).toLocalDate()
            val nextDay = day.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val stop = minOf(endWall, nextDay)
            pending[day.toString()] = pending.getOrDefault(day.toString(), 0) + stop - startWall
            startWall = stop
        }
        lastElapsed = now
        lastWall = wallMs()
    }
}
