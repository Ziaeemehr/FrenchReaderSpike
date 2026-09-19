package com.ziaee.frenchreader.data

import android.content.Context
import com.ziaee.frenchreader.ui.theme.FontScale
import com.ziaee.frenchreader.ui.theme.ReadingBackground
import com.ziaee.frenchreader.ui.theme.ThemeMode

/**
 * Persists the three appearance choices from the Settings screen -- see
 * ROADMAP.md section 7. Follows the same object + SharedPreferences +
 * Context-param shape as VocabPrefs.kt. Each value is stored by its enum
 * name; an unrecognized or missing name falls back to that setting's
 * default (e.g. after an app update adds/renames an enum constant).
 */
object AppearancePrefs {
    private const val PREFS_NAME = "appearance_prefs"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_READING_BACKGROUND = "reading_background"
    private const val KEY_FONT_SCALE = "font_scale"
    private const val KEY_HIGHLIGHT_SAVED_WORDS = "highlight_saved_words"

    fun getThemeMode(context: Context): ThemeMode = read(context, KEY_THEME_MODE, ThemeMode.SYSTEM)
    fun setThemeMode(context: Context, mode: ThemeMode) = write(context, KEY_THEME_MODE, mode.name)

    fun getReadingBackground(context: Context): ReadingBackground =
        read(context, KEY_READING_BACKGROUND, ReadingBackground.SEPIA)
    fun setReadingBackground(context: Context, background: ReadingBackground) =
        write(context, KEY_READING_BACKGROUND, background.name)

    fun getFontScale(context: Context): FontScale = read(context, KEY_FONT_SCALE, FontScale.MEDIUM)
    fun setFontScale(context: Context, scale: FontScale) = write(context, KEY_FONT_SCALE, scale.name)

    fun getHighlightSavedWords(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_HIGHLIGHT_SAVED_WORDS, true)

    fun setHighlightSavedWords(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HIGHLIGHT_SAVED_WORDS, enabled)
            .apply()
    }

    private inline fun <reified T : Enum<T>> read(context: Context, key: String, default: T): T {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(key, null)
            ?: return default
        return try {
            enumValueOf<T>(stored)
        } catch (e: IllegalArgumentException) {
            default
        }
    }

    private fun write(context: Context, key: String, value: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key, value)
            .apply()
    }
}
