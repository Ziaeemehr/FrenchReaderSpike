package com.ziaee.frenchreader.ui.statistics

import android.content.Context
import androidx.room.invalidationTrackerFlow
import com.ziaee.frenchreader.data.ActivityStreakPrefs
import com.ziaee.frenchreader.data.AppDatabase
import com.ziaee.frenchreader.data.StudyLogPrefs
import com.ziaee.frenchreader.studylog.localEpochDayFlow
import java.time.LocalDate
import java.util.TreeMap
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest

internal data class ActivityStreak(val days: Int = 0, val todayMinutes: Double = 0.0, val goalMinutes: Int = 5, val weekMinutes: Double = 0.0)

internal suspend fun loadActivityStreak(context: Context, db: AppDatabase, today: LocalDate = LocalDate.now()): ActivityStreak {
    val cutover = ActivityStreakPrefs.cutover(context, today)
    val goal = ActivityStreakPrefs.goal(context)
    val goalHistory = TreeMap(ActivityStreakPrefs.goalHistory(context))
    val rows = db.activityLogDao().dailyActivity()
    val minutes = dailyActivityMinutes(rows)
    val legacy = rows.filter { it.legacyActive }.map { LocalDate.parse(it.date) }.toSet()
    val week = activityWeekMinutes(minutes, today, StudyLogPrefs.getFirstDayOfWeek(context))
    return ActivityStreak(computeActivityStreak(minutes, legacy, cutover, { day -> goalHistory.floorEntry(day)?.value ?: 5 }, today), minutes[today] ?: 0.0, goal, week)
}

internal fun observeActivityStreak(context: Context, db: AppDatabase) = combine(
    db.invalidationTrackerFlow("review_log", "activity_log", "shadow_attempts", "study_session", emitInitialState = true),
    ActivityStreakPrefs.observeGoal(context),
    StudyLogPrefs.observeFirstDayOfWeek(context),
    localEpochDayFlow()
) { _, _, _, day -> LocalDate.ofEpochDay(day) }.mapLatest { loadActivityStreak(context, db, it) }
