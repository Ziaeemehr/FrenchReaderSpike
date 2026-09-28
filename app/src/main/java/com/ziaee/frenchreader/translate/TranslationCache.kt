package com.ziaee.frenchreader.translate

import android.content.Context
import java.io.File
import java.security.MessageDigest

internal fun translationCacheKey(text: String, sourceLang: String, targetLang: String): String {
    // French keeps the pre-multilingual key so translations cached before v18 are still found.
    val raw = if (sourceLang == "fr") "$text|$targetLang" else "$text|$sourceLang|$targetLang"
    val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

/** Disk cache for paragraph translations, keyed by (text, sourceLang, targetLang). */
class TranslationCache(context: Context) {
    private val dir = File(context.filesDir, "translation_cache").apply { mkdirs() }

    fun get(text: String, sourceLang: String, targetLang: String): String? {
        val file = File(dir, "${translationCacheKey(text, sourceLang, targetLang)}.txt")
        return if (file.exists()) file.readText(Charsets.UTF_8) else null
    }

    fun put(text: String, sourceLang: String, targetLang: String, translation: String) {
        File(dir, "${translationCacheKey(text, sourceLang, targetLang)}.txt").writeText(translation, Charsets.UTF_8)
    }

    fun clearAll() {
        dir.listFiles()?.forEach { it.delete() }
    }
}
