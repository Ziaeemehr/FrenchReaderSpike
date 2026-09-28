package com.ziaee.frenchreader.studylog

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ziaee.frenchreader.data.AppDatabase
import com.ziaee.frenchreader.data.SourceKind
import com.ziaee.frenchreader.data.StudyLogPrefs
import com.ziaee.frenchreader.data.StudySession
import com.ziaee.frenchreader.data.StudySkill
import com.ziaee.frenchreader.data.StudySource
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StudyLogUiState(
    val sessions: List<StudySession> = emptyList(),
    val sources: List<StudySource> = emptyList(),
    val recentSessions: List<StudySession> = emptyList(),
    val range: PeriodRange = periodRange(
        LocalDate.now().toEpochDay(),
        StudyPeriod.WEEK,
        DayOfWeek.SATURDAY
    ),
    val period: StudyPeriod = StudyPeriod.WEEK,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.SATURDAY,
    val totalsBySkill: Map<StudySkill, Int> = StudySkill.entries.associateWith { 0 },
    val sourceTotals: Map<Long?, Int> = emptyMap(),
    val allSourceTotals: Map<Long?, Int> = emptyMap(),
    val buckets: List<DailyStudyBucket> = emptyList()
) {
    val totalMinutes
        get() = sessions.sumOf { it.durationMin }

    val activeSources
        get() = activeSourcesForPicker(sources)
}

sealed interface SaveResult {
    data object Saved : SaveResult

    data object NeedsDailyLimitConfirmation : SaveResult

    data class Invalid(val reason: SessionValidation) : SaveResult
}

class StudyLogViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).studyLogDao()
    private val application = app
    private val period = MutableStateFlow(StudyPeriod.WEEK)
    private val firstDay = MutableStateFlow(StudyLogPrefs.getFirstDayOfWeek(app))
    private val today
        get() = LocalDate.now().toEpochDay()
    private val range = combine(period, firstDay) { selectedPeriod, day ->
        periodRange(today, selectedPeriod, day)
    }
    private val sessions = range.flatMapLatest {
        dao.observeSessions(it.start, it.queryEnd)
    }

    private data class Lists(
        val sources: List<StudySource>,
        val recent: List<StudySession>,
        val allTotals: Map<Long?, Int>
    )

    private val lists = combine(
        dao.observeSources(),
        dao.observeRecent(),
        dao.observeAllSourceTotals()
    ) { sources, recent, totals ->
        Lists(
            sources = sources,
            recent = recent,
            allTotals = totals.associate { it.sourceId to it.minutes }
        )
    }

    val uiState = combine(
        sessions,
        lists,
        range,
        period,
        firstDay
    ) { rows, data, selectedRange, selectedPeriod, selectedFirstDay ->
        StudyLogUiState(
            sessions = rows,
            sources = data.sources,
            recentSessions = data.recent,
            range = selectedRange,
            period = selectedPeriod,
            firstDayOfWeek = selectedFirstDay,
            totalsBySkill = totalsBySkill(rows),
            sourceTotals = totalsBySource(rows),
            allSourceTotals = data.allTotals,
            buckets = dailyBuckets(rows, selectedRange)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StudyLogUiState()
    )

    fun setPeriod(value: StudyPeriod) {
        period.value = value
    }

    fun setFirstDayOfWeek(value: DayOfWeek) {
        firstDay.value = value
        StudyLogPrefs.setFirstDayOfWeek(application, value)
    }

    suspend fun getSession(id: Long) = dao.getSession(id)

    suspend fun save(
        session: StudySession,
        confirmed: Boolean = false
    ): SaveResult {
        val validation = validateSession(session.durationMin, session.date, today)
        if (validation != SessionValidation.Valid) {
            return SaveResult.Invalid(validation)
        }
        val existing = if (session.id == 0L) null else dao.getSession(session.id)
        val excludedId = session.id.takeIf { it != 0L } ?: -1
        val dayTotal = dao.sumMinutesForDateExcluding(session.date, excludedId)
        if (!confirmed && wouldExceedDailyLimit(dayTotal, session.durationMin)) {
            return SaveResult.NeedsDailyLimitConfirmation
        }
        val now = System.currentTimeMillis()
        if (existing == null) {
            dao.insertSession(session.copy(createdAt = now, updatedAt = now))
        } else {
            dao.updateSession(session.copy(createdAt = existing.createdAt, updatedAt = now))
        }
        StudyLogPrefs.setLastSkill(application, session.skill)
        return SaveResult.Saved
    }

    fun delete(
        session: StudySession,
        onDeleted: (StudySession) -> Unit = {}
    ) = viewModelScope.launch {
        dao.deleteSession(session)
        onDeleted(session)
    }

    fun restore(session: StudySession) = viewModelScope.launch {
        dao.insertSession(session.copy(id = 0))
    }

    fun addSource(
        name: String,
        kind: SourceKind = SourceKind.OTHER,
        defaultSkill: StudySkill? = null,
        onResult: (Long?) -> Unit = {}
    ) = viewModelScope.launch {
        if (name.isBlank()) {
            onResult(null)
            return@launch
        }
        runCatching {
            dao.insertSource(
                StudySource(
                    name = name.trim(),
                    kind = kind,
                    defaultSkill = defaultSkill
                )
            )
        }.fold(
            onSuccess = { onResult(it) },
            onFailure = { onResult(null) }
        )
    }

    fun updateSource(
        source: StudySource,
        onResult: (Boolean) -> Unit = {}
    ) = viewModelScope.launch {
        runCatching { dao.updateSource(source) }.fold(
            onSuccess = { onResult(true) },
            onFailure = { onResult(false) }
        )
    }

    fun deleteSource(source: StudySource) = viewModelScope.launch {
        if (dao.countSessionsForSource(source.id) == 0) {
            dao.deleteSource(source)
        }
    }
}
