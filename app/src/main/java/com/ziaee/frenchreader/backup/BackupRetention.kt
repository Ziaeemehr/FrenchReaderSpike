package com.ziaee.frenchreader.backup

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

internal data class BackupCounts(val vocab: Long, val texts: Long, val examAttempts: Long)

internal fun isLargeLoss(previous: BackupCounts, current: BackupCounts): Boolean =
    listOf(previous.vocab to current.vocab, previous.texts to current.texts,
        previous.examAttempts to current.examAttempts).any { (before, after) ->
        before - after >= 5 && after.toDouble() < before.toDouble() * 0.8
    }

/**
 * Which automatic backups survive rotation: the 3 newest, the newest of each of the last 7 days, the
 * newest of each of the last 4 weeks (Monday to Sunday), and today's first one. Counting by time
 * rather than by number means a burst of app closes after a mistake cannot push out every good backup.
 */
internal fun selectBackupsToKeep(timestamps: List<LocalDateTime>, now: LocalDateTime): Set<LocalDateTime> {
    val ordered = timestamps.distinct().sortedDescending()
    val today = now.toLocalDate()
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return buildSet {
        addAll(ordered.take(3))
        addAll(ordered.filter { it > now })
        // The first backup of today too: a mistake made today cannot rotate away the state the day began with.
        ordered.lastOrNull { it.toLocalDate() == today }?.let { add(it) }
        (0L..6L).forEach { day ->
            ordered.firstOrNull { it.toLocalDate() == today.minusDays(day) }?.let { add(it) }
        }
        (0L..3L).forEach { week ->
            val start = monday.minusWeeks(week)
            ordered.firstOrNull { it.toLocalDate() >= start && it.toLocalDate() < start.plusWeeks(1) }
                ?.let { add(it) }
        }
    }
}
