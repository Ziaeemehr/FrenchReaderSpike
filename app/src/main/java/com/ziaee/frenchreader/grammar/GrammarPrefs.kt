package com.ziaee.frenchreader.grammar

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object GrammarPrefs {
    const val PREFS_NAME = "grammar_prefs"
    private const val KEY_STATE = "state"
    private val mutableState = MutableStateFlow(GrammarPrefsState())
    private var loaded = false
    val state: StateFlow<GrammarPrefsState> = mutableState.asStateFlow()

    @Synchronized
    fun load(context: Context) {
        if (!loaded) reload(context)
    }

    @Synchronized
    fun reload(context: Context) {
        mutableState.value = decodeGrammarPrefs(prefs(context).getString(KEY_STATE, "").orEmpty())
        loaded = true
    }

    @Synchronized
    fun toggleFavourite(context: Context, id: String) {
        load(context)
        save(context, mutableState.value.copy(favourites = toggleGrammarFavourite(mutableState.value.favourites, id)))
    }

    @Synchronized
    fun opened(context: Context, id: String) {
        load(context)
        save(context, mutableState.value.copy(recents = openGrammarRecent(mutableState.value.recents, id, System.currentTimeMillis())))
    }

    private fun save(context: Context, value: GrammarPrefsState) {
        prefs(context).edit().putString(KEY_STATE, encodeGrammarPrefs(value)).apply()
        mutableState.value = value
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
