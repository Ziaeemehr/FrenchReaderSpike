package com.ziaee.frenchreader.shadowing

import android.content.Context

enum class SpeechEngineKind(val id: String) { VOSK("vosk"), ANDROID("android") }

object ShadowingPrefs {
    private const val PREFS_NAME = "shadowing_prefs"
    private const val KEY_ENGINE = "engine"

    fun getEngine(context: Context): SpeechEngineKind {
        val id = prefs(context).getString(KEY_ENGINE, SpeechEngineKind.VOSK.id)
        return SpeechEngineKind.entries.firstOrNull { it.id == id } ?: SpeechEngineKind.VOSK
    }

    fun setEngine(context: Context, kind: SpeechEngineKind) =
        prefs(context).edit().putString(KEY_ENGINE, kind.id).apply()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
