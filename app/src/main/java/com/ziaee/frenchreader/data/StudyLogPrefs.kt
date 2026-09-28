package com.ziaee.frenchreader.data

import android.content.Context
import java.time.DayOfWeek

object StudyLogPrefs {
    const val PREFS_NAME = "study_log_prefs"
    private const val KEY_FIRST_DAY = "first_day_of_week"
    private const val KEY_LAST_SKILL = "last_skill"
    private const val KEY_TARGET_PREFIX = "weekly_target_"

    fun getFirstDayOfWeek(context: Context): DayOfWeek =
        runCatching {
            DayOfWeek.valueOf(prefs(context).getString(KEY_FIRST_DAY, null) ?: "SATURDAY")
        }.getOrDefault(DayOfWeek.SATURDAY)

    fun setFirstDayOfWeek(context: Context, value: DayOfWeek) {
        prefs(context).edit().putString(KEY_FIRST_DAY, value.name).apply()
    }

    fun getLastSkill(context: Context): StudySkill =
        runCatching {
            StudySkill.fromCode(
                prefs(context).getString(KEY_LAST_SKILL, null) ?: StudySkill.CO.code
            )
        }.getOrDefault(StudySkill.CO).let { if (it.legacy) StudySkill.VOC else it }

    fun setLastSkill(context: Context, value: StudySkill) {
        prefs(context).edit().putString(KEY_LAST_SKILL, value.code).apply()
    }

    fun getWeeklyTargets(context: Context): Map<StudySkill, Int> =
        StudySkill.entries.associateWith { skill ->
            prefs(context).getInt(KEY_TARGET_PREFIX + skill.code, 0).coerceAtLeast(0)
        }

    fun setWeeklyTarget(context: Context, skill: StudySkill, minutes: Int) {
        prefs(context).edit()
            .putInt(KEY_TARGET_PREFIX + skill.code, minutes.coerceAtLeast(0))
            .apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
