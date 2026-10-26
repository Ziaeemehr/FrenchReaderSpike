package com.ziaee.frenchreader.data

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

object ActivityStreakPrefs {
    private fun prefs(context: Context) = context.getSharedPreferences("activity_streak", Context.MODE_PRIVATE)
    val goals = listOf(3, 5, 10, 15)

    fun goal(context: Context): Int = prefs(context).getInt("goal_minutes", 5).takeIf { it in goals } ?: 5

    fun goalHistory(context: Context): Map<LocalDate, Int> = prefs(context).all.mapNotNull { (key, value) ->
        if (!key.startsWith("goal_on_") || value !is Int || value !in goals) null
        else runCatching { LocalDate.parse(key.removePrefix("goal_on_")) to value }.getOrNull()
    }.toMap()

    @Synchronized
    fun setGoal(context: Context, minutes: Int) {
        require(minutes in goals)
        if (goal(context) == minutes) return
        prefs(context).edit()
            .putInt("goal_on_${LocalDate.now()}", minutes)
            .putInt("goal_minutes", minutes)
            .apply()
    }

    @Synchronized
    fun cutover(context: Context, today: LocalDate): LocalDate {
        val prefs = prefs(context)
        prefs.getString("cutover_date", null)?.let { return LocalDate.parse(it) }
        prefs.edit().putString("cutover_date", today.toString()).apply()
        return today
    }

    fun observeGoal(context: Context) = callbackFlow {
        val prefs = prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "goal_minutes") trySend(goal(context))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(goal(context))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}
