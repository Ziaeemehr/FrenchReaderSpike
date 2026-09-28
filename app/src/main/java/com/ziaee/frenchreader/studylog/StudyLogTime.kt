package com.ziaee.frenchreader.studylog

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

internal fun millisUntilNextLocalMidnight(
    now: Instant,
    zoneId: ZoneId
): Long = ChronoUnit.MILLIS.between(
    now,
    now.atZone(zoneId).toLocalDate().plusDays(1).atStartOfDay(zoneId).toInstant()
).coerceAtLeast(1)

internal fun localEpochDayFlow(
    zoneId: ZoneId = ZoneId.systemDefault(),
    now: () -> Instant = Instant::now
): Flow<Long> = flow {
    var last: Long? = null
    while (currentCoroutineContext().isActive) {
        val instant = now()
        val day = LocalDate.ofInstant(instant, zoneId).toEpochDay()
        if (day != last) {
            emit(day)
            last = day
        }
        // delay() does not advance during deep sleep, so re-check at least every minute.
        delay(millisUntilNextLocalMidnight(instant, zoneId).coerceAtMost(60_000))
    }
}
