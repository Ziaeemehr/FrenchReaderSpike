package com.ziaee.frenchreader.data

import android.content.Context

/**
 * Remembers which vocab list was picked last, so the next "save a new word"
 * sheet defaults to it instead of always starting at "بدون دسته" -- handy
 * while reading through one text and filing several words into the same
 * list in a row. Only applied to brand-new words (see DictionarySheet's
 * `isNew`); editing an already-saved entry always starts from that entry's
 * own list, never from this.
 */
object VocabPrefs {
    private const val PREFS_NAME = "vocab_prefs"
    private const val KEY_LAST_LIST_ID = "last_list_id"
    private const val NONE = -1L

    fun getLastListId(context: Context): Long? {
        val value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_LIST_ID, NONE)
        return if (value == NONE) null else value
    }

    fun setLastListId(context: Context, listId: Long?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_LIST_ID, listId ?: NONE)
            .apply()
    }
}
