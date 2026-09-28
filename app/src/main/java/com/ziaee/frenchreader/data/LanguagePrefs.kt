package com.ziaee.frenchreader.data

import android.content.Context
import android.content.SharedPreferences
import com.ziaee.frenchreader.language.LanguageCatalog
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

internal fun legacyMeaningLanguageCode(language: VocabPrefs.MeaningLanguage): String =
    when (language) {
        VocabPrefs.MeaningLanguage.PERSIAN -> "fa"
        VocabPrefs.MeaningLanguage.ENGLISH -> "en"
    }

object LanguagePrefs {
    private const val PREFS_NAME = "language_prefs"
    private const val KEY_TARGET_LANGUAGE = "target_language"
    private const val KEY_KNOWN_LANGUAGE = "known_language"
    private const val DEFAULT_KNOWN_LANGUAGE = "fa"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isConfigured(context: Context): Boolean {
        val preferences = prefs(context)
        return preferences.contains(KEY_TARGET_LANGUAGE) &&
            preferences.contains(KEY_KNOWN_LANGUAGE)
    }

    fun getTargetLanguage(context: Context): String =
        prefs(context).getString(KEY_TARGET_LANGUAGE, null)
            ?.takeIf(LanguageCatalog::isSupportedTarget)
            ?: LanguageCatalog.DEFAULT_TARGET

    fun setTargetLanguage(context: Context, code: String) {
        require(LanguageCatalog.isSupportedTarget(code)) { "Unsupported target language: $code" }
        prefs(context).edit().putString(KEY_TARGET_LANGUAGE, code).apply()
    }

    fun getKnownLanguage(context: Context): String =
        prefs(context).getString(KEY_KNOWN_LANGUAGE, null)
            ?.takeIf { it in LanguageCatalog.knownLanguages }
            ?: DEFAULT_KNOWN_LANGUAGE

    fun setKnownLanguage(context: Context, code: String) {
        require(code in LanguageCatalog.knownLanguages) { "Unsupported known language: $code" }
        prefs(context).edit().putString(KEY_KNOWN_LANGUAGE, code).apply()
        when (code) {
            "fa" -> VocabPrefs.setMeaningLanguage(context, VocabPrefs.MeaningLanguage.PERSIAN)
            "en" -> VocabPrefs.setMeaningLanguage(context, VocabPrefs.MeaningLanguage.ENGLISH)
        }
    }

    fun setLanguages(context: Context, targetLanguage: String, knownLanguage: String) {
        require(LanguageCatalog.isSupportedTarget(targetLanguage)) {
            "Unsupported target language: $targetLanguage"
        }
        require(knownLanguage in LanguageCatalog.knownLanguages) {
            "Unsupported known language: $knownLanguage"
        }
        require(targetLanguage != knownLanguage) { "Target and known languages must differ" }
        prefs(context).edit()
            .putString(KEY_TARGET_LANGUAGE, targetLanguage)
            .putString(KEY_KNOWN_LANGUAGE, knownLanguage)
            .apply()
        when (knownLanguage) {
            "fa" -> VocabPrefs.setMeaningLanguage(context, VocabPrefs.MeaningLanguage.PERSIAN)
            "en" -> VocabPrefs.setMeaningLanguage(context, VocabPrefs.MeaningLanguage.ENGLISH)
        }
    }

    fun observeTargetLanguage(context: Context): Flow<String> = callbackFlow {
        val appContext = context.applicationContext
        val preferences = prefs(appContext)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_TARGET_LANGUAGE) {
                trySend(getTargetLanguage(appContext))
            }
        }

        trySend(getTargetLanguage(appContext))
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun migrateLegacyIfNeeded(context: Context) {
        if (isConfigured(context)) return

        prefs(context).edit()
            .putString(KEY_TARGET_LANGUAGE, LanguageCatalog.DEFAULT_TARGET)
            .putString(
                KEY_KNOWN_LANGUAGE,
                legacyMeaningLanguageCode(VocabPrefs.getMeaningLanguage(context)),
            )
            .apply()
    }
}
