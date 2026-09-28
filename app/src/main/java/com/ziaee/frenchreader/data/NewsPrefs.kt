package com.ziaee.frenchreader.data

import android.content.Context
import com.ziaee.frenchreader.news.DEUTSCHLANDFUNK_SOURCE_ID
import com.ziaee.frenchreader.news.DW_LANGSAM_SOURCE_ID
import com.ziaee.frenchreader.news.FRANCE_INFO_SOURCE_ID
import com.ziaee.frenchreader.news.RFI_FACILE_SOURCE_ID
import com.ziaee.frenchreader.news.TAGESSCHAU_SOURCE_ID
import com.ziaee.frenchreader.news.newsSourcesFor

object NewsPrefs {
    private const val PREFS_NAME = "news_prefs"
    private const val KEY_ENABLED_SOURCES = "enabled_sources"
    private const val KEY_KEYWORDS = "keywords"
    private const val KEY_MATCHING_FIRST = "matching_first"

    val DEFAULT_ENABLED_SOURCE_IDS = setOf(RFI_FACILE_SOURCE_ID, FRANCE_INFO_SOURCE_ID, "france24")
    private val DEFAULT_ENABLED_BY_LANGUAGE = mapOf(
        "fr" to DEFAULT_ENABLED_SOURCE_IDS,
        "de" to setOf(DW_LANGSAM_SOURCE_ID, TAGESSCHAU_SOURCE_ID, DEUTSCHLANDFUNK_SOURCE_ID)
    )

    /** One stored set holds the enabled IDs of every language (IDs are unique across languages),
     * plus a "configured:<lang>" marker once that language's choices were saved. A language that was
     * never configured uses its defaults; French predates the marker, so any stored set counts. */
    fun getEnabledSourceIds(context: Context, language: String): Set<String> =
        enabledForLanguage(storedIds(context), language)

    fun setEnabledSourceIds(context: Context, language: String, value: Set<String>) {
        prefs(context).edit()
            .putStringSet(KEY_ENABLED_SOURCES, withLanguageChoice(storedIds(context), language, value))
            .apply()
    }

    private fun storedIds(context: Context): Set<String>? =
        prefs(context).getStringSet(KEY_ENABLED_SOURCES, null)?.toSet()

    private fun marker(language: String) = "configured:$language"

    internal fun enabledForLanguage(stored: Set<String>?, language: String): Set<String> {
        val languageIds = newsSourcesFor(language).mapTo(mutableSetOf()) { it.id }
        val configured = stored != null && (marker(language) in stored || language == "fr")
        return if (configured) stored!!.intersect(languageIds) else DEFAULT_ENABLED_BY_LANGUAGE[language].orEmpty()
    }

    internal fun withLanguageChoice(stored: Set<String>?, language: String, value: Set<String>): Set<String> {
        val languageIds = newsSourcesFor(language).mapTo(mutableSetOf()) { it.id }
        // Keep French's implicit defaults if French was never saved before this first write.
        val base = stored ?: DEFAULT_ENABLED_SOURCE_IDS
        return (base - languageIds) + (value intersect languageIds) + marker(language)
    }

    fun getKeywords(context: Context): String = prefs(context).getString(KEY_KEYWORDS, "").orEmpty()

    fun setKeywords(context: Context, value: String) = prefs(context).edit().putString(KEY_KEYWORDS, value).apply()

    fun getMatchingFirst(context: Context): Boolean = prefs(context).getBoolean(KEY_MATCHING_FIRST, true)

    fun setMatchingFirst(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_MATCHING_FIRST, value).apply()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
