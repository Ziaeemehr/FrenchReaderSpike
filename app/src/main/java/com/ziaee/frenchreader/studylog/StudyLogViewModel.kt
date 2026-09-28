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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

data class StudyLogUiState(
    val today: Long = LocalDate.now().toEpochDay(),
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
    val buckets: List<DailyStudyBucket> = emptyList(),
    val weeklyTargets: Map<StudySkill, Int> = StudySkill.entries.associateWith { 0 },
    val cumulative: Map<StudySkill, List<CumulativePoint>> = emptyMap(),
    val activitySessions: List<StudySession> = emptyList(),
    val comparison: WeekComparison = WeekComparison(
        0,
        0,
        StudySkill.entries.associateWith { 0 }
    ),
    val streaks: Streaks = Streaks(0, 0),
    val activeDays: Int = 0,
    val hasEntries: Boolean = false
) {
    val totalMinutes
        get() = sessions.sumOf { it.durationMin }

    val activeSources
        get() = activeSourcesForPicker(sources)
}

data class StudySourceUiState(
    val sources: List<StudySource> = emptyList(),
    val totals: Map<Long?, Int> = emptyMap()
)

sealed interface SaveResult {
    data object Saved : SaveResult

    data object NeedsDailyLimitConfirmation : SaveResult

    data class Invalid(val reason: SessionValidation) : SaveResult

    data object InFlight : SaveResult
}

class StudyLogViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).studyLogDao()
    private val application = app
    private val period = MutableStateFlow(StudyPeriod.WEEK)
    private val firstDay = MutableStateFlow(StudyLogPrefs.getFirstDayOfWeek(app))
    private val today = localEpochDayFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = LocalDate.now().toEpochDay()
    )
    val currentDay = today
    private val targets = MutableStateFlow(StudyLogPrefs.getWeeklyTargets(app))
    private val firstDate = dao.observeFirstSessionDate()
    private val saveMutex = Mutex()
    private val range = combine(today, period, firstDay, firstDate) { currentDay, selectedPeriod, day, first ->
        periodRange(currentDay, selectedPeriod, day, first) ?: PeriodRange(currentDay, currentDay, currentDay)
    }
    private val sessions = range.flatMapLatest { selectedRange ->
        dao.observeSessions(selectedRange.start, selectedRange.queryEnd)
    }
    private val allSessions = combine(firstDate, today) { first, currentDay -> first to currentDay }
        .flatMapLatest { (first, currentDay) ->
            if (first == null) flowOf(emptyList()) else dao.observeSessions(first, currentDay)
        }

    val sources = dao.observeSources().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val sourceUiState = combine(sources, dao.observeAllSourceTotals()) { sourceRows, totals ->
        StudySourceUiState(
            sources = sourceRows,
            totals = totals.associate { it.sourceId to it.minutes }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StudySourceUiState()
    )

    private data class Lists(
        val sourceState: StudySourceUiState,
        val recent: List<StudySession>,
        val allSessions: List<StudySession>
    )

    private data class Selection(
        val period: StudyPeriod,
        val firstDay: DayOfWeek,
        val targets: Map<StudySkill, Int>
    )

    private val lists = combine(
        sourceUiState,
        dao.observeRecent(),
        allSessions
    ) { sourceState, recent, allSessions ->
        Lists(
            sourceState = sourceState,
            recent = recent,
            allSessions = allSessions
        )
    }

    private val selection = combine(period, firstDay, targets) { selectedPeriod, day, selectedTargets ->
        Selection(selectedPeriod, day, selectedTargets)
    }

    val uiState = combine(
        sessions,
        lists,
        range,
        selection,
        today
    ) { rows, data, selectedRange, selected, currentDay ->
        StudyLogUiState(
            today = currentDay,
            sessions = rows,
            sources = data.sourceState.sources,
            recentSessions = data.recent,
            range = selectedRange,
            period = selected.period,
            firstDayOfWeek = selected.firstDay,
            totalsBySkill = totalsBySkill(rows),
            sourceTotals = totalsBySource(rows),
            allSourceTotals = data.sourceState.totals,
            buckets = periodBuckets(rows, selectedRange, selected.period, selected.firstDay),
            weeklyTargets = selected.targets,
            cumulative = cumulativeSeries(rows, selectedRange, selected.period, selected.firstDay),
            activitySessions = data.allSessions,
            comparison = weekOverWeek(data.allSessions, currentDay, selected.firstDay),
            streaks = streaks(data.allSessions, currentDay),
            activeDays = activeDayCount(rows),
            hasEntries = if (selected.period == StudyPeriod.ALL) data.allSessions.isNotEmpty() else true
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

    fun setWeeklyTarget(skill: StudySkill, minutes: Int) {
        StudyLogPrefs.setWeeklyTarget(application, skill, minutes)
        targets.value = StudyLogPrefs.getWeeklyTargets(application)
    }

    suspend fun getSession(id: Long) = dao.getSession(id)

    suspend fun save(
        session: StudySession,
        confirmed: Boolean = false
    ): SaveResult {
        if (!saveMutex.tryLock()) return SaveResult.InFlight
        try {
            val validation = validateSession(session.durationMin, session.date, today.value)
            if (validation != SessionValidation.Valid) {
                return SaveResult.Invalid(validation)
            }
            val existing = if (session.id == 0L) null else dao.getSession(session.id)
            if (session.id != 0L && existing == null) {
                return SaveResult.Invalid(SessionValidation.MissingEntry)
            }
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
        } finally {
            saveMutex.unlock()
        }
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
        val normalized = normalizeSourceName(name)
        if (normalized !is SourceNameValidation.Valid) {
            onResult(null)
            return@launch
        }
        runCatching {
            dao.insertSource(
                StudySource(
                    name = normalized.name,
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
        val normalized = normalizeSourceName(source.name)
        if (normalized !is SourceNameValidation.Valid) {
            onResult(false)
            return@launch
        }
        runCatching { dao.updateSource(source.copy(name = normalized.name)) }.fold(
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
