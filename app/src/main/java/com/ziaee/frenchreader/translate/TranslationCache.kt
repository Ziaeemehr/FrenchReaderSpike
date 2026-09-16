package com.ziaee.frenchreader.translate

import android.content.Context
import java.io.File
import java.security.MessageDigest

/** Disk cache for paragraph translations, keyed by (text, targetLang). */
class TranslationCache(context: Context) {
    private val dir = File(context.filesDir, "translation_cache").apply { mkdirs() }

    private fun keyFor(text: String, targetLang: String): String {
        val raw = "$text|$targetLang"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun get(text: String, targetLang: String): String? {
        val file = File(dir, "${keyFor(text, targetLang)}.txt")
        return if (file.exists()) file.readText(Charsets.UTF_8) else null
    }

    fun put(text: String, targetLang: String, translation: String) {
        File(dir, "${keyFor(text, targetLang)}.txt").writeText(translation, Charsets.UTF_8)
    }

    fun clearAll() {
        dir.listFiles()?.forEach { it.delete() }
    }
}
