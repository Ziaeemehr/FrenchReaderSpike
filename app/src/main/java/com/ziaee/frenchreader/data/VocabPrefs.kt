package com.ziaee.frenchreader.data

import android.content.Context

/**
 * Remembers which vocab list was picked last, so the next "save a new word"
 * sheet defaults to it instead of always starting at "بدون دسته" -- handy
 * while reading through one text and filing several words into the same
 * list in a row. Only applied to brand-new words (see DictionarySheet's
 * `isNew`); editing an already-saved entry always starts from that entry's
 * own list, never from this.
 */
object VocabPrefs {
    private const val PREFS_NAME = "vocab_prefs"
    private const val KEY_LAST_LIST_ID = "last_list_id"
    private const val NONE = -1L
    private const val KEY_INTERVALS = "review_intervals"
    private const val KEY_MAX_NEW = "max_new_cards"
    private const val KEY_DAILY_GOAL = "daily_review_goal"
    private const val KEY_AUDIO_AUTOPLAY = "review_audio_autoplay"
    private const val KEY_MEANING_LANGUAGE = "meaning_language"
    private const val KEY_REMINDER_ENABLED = "review_reminder_enabled"
    private const val KEY_REMINDER_HOUR = "review_reminder_hour"
    private const val KEY_REMINDER_MINUTE = "review_reminder_minute"
    private const val KEY_NEW_COUNT_DATE = "new_count_date"
    private const val KEY_NEW_COUNT = "new_count"
    private const val KEY_STUDY_TIME_DATE = "study_time_date"
    private const val KEY_STUDY_TIME_MS = "study_time_ms"

    val DEFAULT_INTERVAL_DAYS = listOf(1L, 2L, 4L, 8L, 16L)
    const val DEFAULT_MAX_NEW_CARDS = 10
    const val DEFAULT_DAILY_GOAL = 20

    enum class MeaningLanguage { PERSIAN, ENGLISH }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getLastListId(context: Context): Long? {
        val value = prefs(context).getLong(KEY_LAST_LIST_ID, NONE)
        return if (value == NONE) null else value
    }

    fun setLastListId(context: Context, listId: Long?) {
        prefs(context).edit()
            .putLong(KEY_LAST_LIST_ID, listId ?: NONE)
            .apply()
    }

    fun getIntervals(context: Context): List<Long> =
        prefs(context).getString(KEY_INTERVALS, null)
            ?.split(',')
            ?.mapNotNull(String::toLongOrNull)
            ?.takeIf { it.size == 5 && it.all { days -> days > 0 } }
            ?: DEFAULT_INTERVAL_DAYS

    fun setIntervals(context: Context, days: List<Long>) {
        require(days.size == 5 && days.all { it > 0 })
        prefs(context).edit().putString(KEY_INTERVALS, days.joinToString(",")).apply()
    }

    fun resetIntervals(context: Context) = prefs(context).edit().remove(KEY_INTERVALS).apply()
    fun getMaxNewCards(context: Context) = prefs(context).getInt(KEY_MAX_NEW, DEFAULT_MAX_NEW_CARDS)
    fun setMaxNewCards(context: Context, value: Int) = prefs(context).edit().putInt(KEY_MAX_NEW, value.coerceAtLeast(0)).apply()
    fun getDailyGoal(context: Context) = prefs(context).getInt(KEY_DAILY_GOAL, DEFAULT_DAILY_GOAL)
    fun setDailyGoal(context: Context, value: Int) = prefs(context).edit().putInt(KEY_DAILY_GOAL, value.coerceAtLeast(1)).apply()
    fun getAudioAutoplay(context: Context) = prefs(context).getBoolean(KEY_AUDIO_AUTOPLAY, false)
    fun setAudioAutoplay(context: Context, value: Boolean) = prefs(context).edit().putBoolean(KEY_AUDIO_AUTOPLAY, value).apply()
    fun getMeaningLanguage(context: Context) = runCatching {
        MeaningLanguage.valueOf(prefs(context).getString(KEY_MEANING_LANGUAGE, null) ?: MeaningLanguage.PERSIAN.name)
    }.getOrDefault(MeaningLanguage.PERSIAN)
    fun setMeaningLanguage(context: Context, value: MeaningLanguage) = prefs(context).edit().putString(KEY_MEANING_LANGUAGE, value.name).apply()
    fun getReminderEnabled(context: Context) = prefs(context).getBoolean(KEY_REMINDER_ENABLED, false)
    fun setReminderEnabled(context: Context, value: Boolean) = prefs(context).edit().putBoolean(KEY_REMINDER_ENABLED, value).apply()
    fun getReminderHour(context: Context) = prefs(context).getInt(KEY_REMINDER_HOUR, 19)
    fun getReminderMinute(context: Context) = prefs(context).getInt(KEY_REMINDER_MINUTE, 0)
    fun setReminderTime(context: Context, hour: Int, minute: Int) = prefs(context).edit()
        .putInt(KEY_REMINDER_HOUR, hour).putInt(KEY_REMINDER_MINUTE, minute).apply()

    fun getNewReviewedToday(context: Context, date: String): Int =
        if (prefs(context).getString(KEY_NEW_COUNT_DATE, null) == date) prefs(context).getInt(KEY_NEW_COUNT, 0) else 0

    fun incrementNewReviewed(context: Context, date: String) {
        val current = getNewReviewedToday(context, date)
        prefs(context).edit().putString(KEY_NEW_COUNT_DATE, date).putInt(KEY_NEW_COUNT, current + 1).apply()
    }

    fun decrementNewReviewed(context: Context, date: String) {
        val current = getNewReviewedToday(context, date)
        if (current > 0) prefs(context).edit().putString(KEY_NEW_COUNT_DATE, date).putInt(KEY_NEW_COUNT, current - 1).apply()
    }

    fun getStudyTimeMsToday(context: Context, date: String): Long =
        if (prefs(context).getString(KEY_STUDY_TIME_DATE, null) == date) prefs(context).getLong(KEY_STUDY_TIME_MS, 0L) else 0L

    fun addStudyTimeMs(context: Context, date: String, deltaMs: Long) {
        val current = getStudyTimeMsToday(context, date)
        prefs(context).edit().putString(KEY_STUDY_TIME_DATE, date).putLong(KEY_STUDY_TIME_MS, current + deltaMs).apply()
    }
}
