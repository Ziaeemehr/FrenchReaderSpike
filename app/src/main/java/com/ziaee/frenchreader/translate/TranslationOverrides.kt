package com.ziaee.frenchreader.translate

import android.content.Context
import java.io.File
import java.security.MessageDigest

/** User corrections, keyed by (text, targetLang). */
class TranslationOverrides(context: Context) {
    private val dir = File(context.filesDir, "translation_overrides").apply { mkdirs() }

    private fun keyFor(text: String, targetLang: String): String {
        val raw = "$text|$targetLang"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun getOverride(text: String, targetLang: String): String? {
        val file = File(dir, "${keyFor(text, targetLang)}.txt")
        return if (file.exists()) file.readText(Charsets.UTF_8) else null
    }

    fun putOverride(text: String, targetLang: String, translation: String) {
        File(dir, "${keyFor(text, targetLang)}.txt").writeText(translation, Charsets.UTF_8)
    }

    fun removeOverride(text: String, targetLang: String) {
        File(dir, "${keyFor(text, targetLang)}.txt").delete()
    }

    fun clearAll() {
        dir.listFiles()?.forEach { it.delete() }
    }
}
