package com.ziaee.frenchreader.translate.prepare

import java.text.Normalizer
import java.util.Locale

/** Matches display titles only; callers must not pass flashcard contents. */
fun preparationTitleMatches(title: String, query: String): Boolean {
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
    return normalize(title).contains(normalize(query.trim()))
}
