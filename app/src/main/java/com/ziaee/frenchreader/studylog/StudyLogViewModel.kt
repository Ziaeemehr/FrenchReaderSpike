package com.ziaee.frenchreader.studylog

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
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
    private val database = AppDatabase.get(app)
    private val dao = database.studyLogDao()
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
    private val saveMutex = Mutex()
    private val allSessions = dao.observeSessions(Long.MIN_VALUE, Long.MAX_VALUE)

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
        lists,
        selection,
        today
    ) { data, selected, currentDay ->
        val activitySessions = data.allSessions.filter { it.date <= currentDay }
        val selectedRange = periodRange(
            currentDay, selected.period, selected.firstDay, activitySessions
        ) ?: PeriodRange(currentDay, currentDay, currentDay)
        val rows = activitySessions.filter { it.date in selectedRange.start..selectedRange.queryEnd }
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
            activitySessions = activitySessions,
            comparison = weekOverWeek(activitySessions, currentDay, selected.firstDay),
            streaks = streaks(activitySessions, currentDay),
            activeDays = activeDayCount(rows),
            hasEntries = if (selected.period == StudyPeriod.ALL) activitySessions.isNotEmpty() else true
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
            return database.withTransaction {
                val validation = validateSession(session.durationMin, session.date, LocalDate.now().toEpochDay())
                if (validation != SessionValidation.Valid) {
                    return@withTransaction SaveResult.Invalid(validation)
                }
                if (session.sourceId != null && dao.getSource(session.sourceId) == null) {
                    return@withTransaction SaveResult.Invalid(SessionValidation.MissingSource)
                }
                val existing = if (session.id == 0L) null else dao.getSession(session.id)
                if (session.id != 0L && existing == null) {
                    return@withTransaction SaveResult.Invalid(SessionValidation.MissingEntry)
                }
                val excludedId = session.id.takeIf { it != 0L } ?: -1
                val dayTotal = dao.sumMinutesForDateExcluding(session.date, excludedId)
                if (!confirmed && wouldExceedDailyLimit(dayTotal, session.durationMin)) {
                    return@withTransaction SaveResult.NeedsDailyLimitConfirmation
                }
                val now = System.currentTimeMillis()
                if (existing == null) {
                    dao.insertSession(session.copy(createdAt = now, updatedAt = now))
                } else {
                    dao.updateSession(session.copy(createdAt = existing.createdAt, updatedAt = now))
                }
                StudyLogPrefs.setLastSkill(application, session.skill)
                SaveResult.Saved
            }
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
        database.withTransaction {
            val sourceId = session.sourceId?.takeIf { dao.getSource(it) != null }
            dao.insertSession(session.copy(id = 0, sourceId = sourceId))
        }
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
        runCatching {
            dao.updateSourceDetails(source.id, normalized.name, source.kind, source.defaultSkill)
        }.fold(
            onSuccess = { onResult(it > 0) },
            onFailure = { onResult(false) }
        )
    }

    fun setSourceArchived(source: StudySource, archived: Boolean) = viewModelScope.launch {
        dao.setSourceArchived(source.id, archived)
    }

    fun deleteSource(source: StudySource) = viewModelScope.launch {
        dao.deleteSourceIfUnused(source.id)
    }
}
