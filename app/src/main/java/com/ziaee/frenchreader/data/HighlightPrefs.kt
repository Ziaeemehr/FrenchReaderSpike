package com.ziaee.frenchreader.data

import android.content.Context

object HighlightPrefs {
    private const val PREFS_NAME = "highlight_prefs"
    private const val KEY_LAST_COLOR = "last_color"
    const val DEFAULT_COLOR_KEY = "yellow"

    fun getLastColorKey(context: Context): String {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_COLOR, null)
        return stored?.takeIf { it in HighlightColors.keys } ?: DEFAULT_COLOR_KEY
    }

    fun setLastColorKey(context: Context, colorKey: String) {
        if (colorKey !in HighlightColors.keys) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_COLOR, colorKey)
            .apply()
    }
}
