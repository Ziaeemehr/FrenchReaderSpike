package com.ziaee.frenchreader.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

enum class AppLanguage(val languageTags: String) {
    SYSTEM(""), FA("fa"), FR("fr"), EN("en")
}

internal fun parseAppLanguage(value: String?): AppLanguage =
    AppLanguage.entries.firstOrNull { it.name == value } ?: AppLanguage.SYSTEM

object LocalePrefs {
    private const val PREFS_NAME = "locale_prefs"
    private const val KEY_LANGUAGE = "app_language"

    fun get(context: Context): AppLanguage = parseAppLanguage(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, null)
    )

    fun set(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.name)
            .apply()
    }
}

fun applyAppLanguage(language: AppLanguage) {
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.languageTags))
}
