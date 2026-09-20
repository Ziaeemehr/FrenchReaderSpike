package com.ziaee.frenchreader.data

import android.content.Context
import com.ziaee.frenchreader.news.FRANCE_INFO_SOURCE_ID
import com.ziaee.frenchreader.news.RFI_FACILE_SOURCE_ID

object NewsPrefs {
    private const val PREFS_NAME = "news_prefs"
    private const val KEY_ENABLED_SOURCES = "enabled_sources"
    private const val KEY_KEYWORDS = "keywords"
    private const val KEY_MATCHING_FIRST = "matching_first"

    val DEFAULT_ENABLED_SOURCE_IDS = setOf(RFI_FACILE_SOURCE_ID, FRANCE_INFO_SOURCE_ID, "france24")

    fun getEnabledSourceIds(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_ENABLED_SOURCES, DEFAULT_ENABLED_SOURCE_IDS)?.toSet()
            ?: DEFAULT_ENABLED_SOURCE_IDS

    fun setEnabledSourceIds(context: Context, value: Set<String>) =
        prefs(context).edit().putStringSet(KEY_ENABLED_SOURCES, value).apply()

    fun getKeywords(context: Context): String = prefs(context).getString(KEY_KEYWORDS, "").orEmpty()

    fun setKeywords(context: Context, value: String) = prefs(context).edit().putString(KEY_KEYWORDS, value).apply()

    fun getMatchingFirst(context: Context): Boolean = prefs(context).getBoolean(KEY_MATCHING_FIRST, true)

    fun setMatchingFirst(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_MATCHING_FIRST, value).apply()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
