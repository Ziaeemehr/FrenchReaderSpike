package com.ziaee.frenchreader.data

import android.content.Context

object LlmAssistantPrefs {
    private const val PREFS_NAME = "llm_assistant_prefs"
    private const val KEY_MODEL_DOWNLOADED = "model_downloaded"

    fun isModelDownloaded(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_MODEL_DOWNLOADED, false)

    fun setModelDownloaded(context: Context, downloaded: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_MODEL_DOWNLOADED, downloaded)
            .apply()
    }
}
