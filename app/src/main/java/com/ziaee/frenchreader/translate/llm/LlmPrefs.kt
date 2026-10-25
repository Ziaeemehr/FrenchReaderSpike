package com.ziaee.frenchreader.translate.llm

import android.content.Context

enum class TranslationEngine { GOOGLE, COMPUTER, ONLINE }

class LlmPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("llm_prefs", Context.MODE_PRIVATE)

    var engine: TranslationEngine
        get() = runCatching { TranslationEngine.valueOf(prefs.getString("engine", null).orEmpty()) }
            .getOrDefault(TranslationEngine.GOOGLE)
        set(value) { prefs.edit().putString("engine", value.name).apply() }
    var computerUrl: String
        get() = prefs.getString("computerUrl", "http://127.0.0.1:8080")!!
        set(value) { prefs.edit().putString("computerUrl", value).apply() }
    var computerModel: String
        get() = prefs.getString("computerModel", "gemma4")!!
        set(value) { prefs.edit().putString("computerModel", value).apply() }
    var onlineProvider: LlmProvider
        get() = runCatching { LlmProvider.valueOf(prefs.getString("onlineProvider", null).orEmpty()) }
            .getOrDefault(LlmProvider.OPENROUTER)
        set(value) {
            if (value != onlineProvider) prefs.edit().putString("onlineProvider", value.name).remove("onlineModel").apply()
        }
    var customBaseUrl: String
        get() = prefs.getString("customBaseUrl", "")!!
        set(value) { prefs.edit().putString("customBaseUrl", value).apply() }
    var onlineModel: String
        get() = prefs.getString("onlineModel", onlineProvider.defaultModel)!!
        set(value) { prefs.edit().putString("onlineModel", value).apply() }
    var experimentalAccepted: Boolean
        get() = prefs.getBoolean("experimentalAccepted", false)
        set(value) { prefs.edit().putBoolean("experimentalAccepted", value).apply() }

    val currentModel: String?
        get() = when (engine) {
            TranslationEngine.GOOGLE -> null
            TranslationEngine.COMPUTER -> computerModel
            TranslationEngine.ONLINE -> onlineModel
        }
}
