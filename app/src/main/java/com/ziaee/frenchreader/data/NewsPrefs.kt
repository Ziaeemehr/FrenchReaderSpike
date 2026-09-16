package com.ziaee.frenchreader.data

import android.content.Context

/**
 * Remembers which news source was last picked from "دریافت خبر امروز" (so
 * the picker can default to it next time), and the guid/link of the most
 * recently imported item *per source* -- so tapping the same source again
 * before it has published anything new shows "خبر جدیدی نیست" instead of
 * silently creating a duplicate text. See ROADMAP.md section 1.
 */
object NewsPrefs {
    private const val PREFS_NAME = "news_prefs"
    private const val KEY_LAST_SOURCE = "last_source_id"
    private fun lastGuidKey(sourceId: String) = "last_guid_$sourceId"

    fun getLastSourceId(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_LAST_SOURCE, null)

    fun setLastSourceId(context: Context, sourceId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LAST_SOURCE, sourceId).apply()
    }

    fun getLastImportedGuid(context: Context, sourceId: String): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(lastGuidKey(sourceId), null)

    fun setLastImportedGuid(context: Context, sourceId: String, guid: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(lastGuidKey(sourceId), guid).apply()
    }
}
