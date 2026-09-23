package com.ziaee.frenchreader.data

import android.content.Context

object TtsCachePrefs {
    private const val PREFS_NAME = "tts_cache_prefs"
    private const val KEY_MAX_SIZE_MB = "max_size_mb"
    private const val KEY_MAX_AGE_DAYS = "max_age_days"

    fun getMaxSizeMb(context: Context): Int = prefs(context).getInt(KEY_MAX_SIZE_MB, 500)

    fun setMaxSizeMb(context: Context, value: Int) = prefs(context).edit().putInt(KEY_MAX_SIZE_MB, value).apply()

    fun getMaxAgeDays(context: Context): Int = prefs(context).getInt(KEY_MAX_AGE_DAYS, 30)

    fun setMaxAgeDays(context: Context, value: Int) = prefs(context).edit().putInt(KEY_MAX_AGE_DAYS, value).apply()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
