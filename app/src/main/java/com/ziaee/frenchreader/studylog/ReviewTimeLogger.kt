package com.ziaee.frenchreader.studylog

import android.content.Context
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.data.AppDatabase
import com.ziaee.frenchreader.data.SourceKind
import com.ziaee.frenchreader.data.StudySession
import com.ziaee.frenchreader.data.StudySkill
import com.ziaee.frenchreader.data.StudySource
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Mirrors word-review time into the study log: one VOC entry per day under the
 * "Word review" source, set to that day's total review time.
 */
object ReviewTimeLogger {
    // Outlives the review ViewModel, which may already be cleared when a session ends.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    fun sync(context: Context, date: LocalDate, totalMs: Long) {
        val minutes = reviewMinutes(totalMs)
        if (minutes == 0) return
        val app = context.applicationContext
        scope.launch {
            mutex.withLock {
                runCatching { write(app, date.toEpochDay(), minutes) }
            }
        }
    }

    private suspend fun write(context: Context, epochDay: Long, minutes: Int) {
        val dao = AppDatabase.get(context).studyLogDao()
        val name = context.getString(R.string.study_log_review_source)
        val sourceId = dao.getSourceByName(name)?.id
            ?: dao.insertSource(StudySource(name = name, kind = SourceKind.APP, defaultSkill = StudySkill.VOC))
        val existing = dao.findSessionForSource(epochDay, sourceId)
        if (existing == null) {
            dao.insertSession(
                StudySession(date = epochDay, durationMin = minutes, skill = StudySkill.VOC, sourceId = sourceId)
            )
        } else if (existing.durationMin != minutes) {
            dao.updateSession(existing.copy(durationMin = minutes, updatedAt = System.currentTimeMillis()))
        }
    }
}

internal fun reviewMinutes(totalMs: Long): Int =
    if (totalMs <= 0) 0 else (totalMs / 60_000.0).roundToInt().coerceIn(1, 1440)
