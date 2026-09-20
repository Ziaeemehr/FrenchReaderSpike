package com.ziaee.frenchreader.data

import android.content.Context

object XttsPrefs {
    private const val PREFS_NAME = "xtts_prefs"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_SPEAKER = "speaker"
    private const val KEY_TOKEN = "token"

    fun getServerUrl(context: Context): String = prefs(context).getString(KEY_SERVER_URL, "").orEmpty()

    fun setServerUrl(context: Context, value: String) = prefs(context).edit().putString(KEY_SERVER_URL, value).apply()

    fun getSpeaker(context: Context): String = prefs(context).getString(KEY_SPEAKER, "Claribel Dervla") ?: "Claribel Dervla"

    fun setSpeaker(context: Context, value: String) = prefs(context).edit().putString(KEY_SPEAKER, value).apply()

    fun getToken(context: Context): String = prefs(context).getString(KEY_TOKEN, "").orEmpty()

    fun setToken(context: Context, value: String) = prefs(context).edit().putString(KEY_TOKEN, value).apply()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
