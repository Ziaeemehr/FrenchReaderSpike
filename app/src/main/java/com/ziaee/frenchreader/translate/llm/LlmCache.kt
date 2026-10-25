package com.ziaee.frenchreader.translate.llm

import android.content.Context
import com.ziaee.frenchreader.translate.polishTranslation
import java.io.File
import java.security.MessageDigest

private fun cacheHash(raw: String): String = MessageDigest.getInstance("SHA-256")
    .digest(raw.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

internal fun translationCacheKey(model: String, lang: String, text: String): String = cacheHash("t|$model|$lang|$text")
internal fun meaningCacheKey(model: String, lang: String, word: String, sentence: String?): String =
    cacheHash("m|$model|$lang|$word|$sentence")

class LlmCache(context: Context) {
    private val dir = File(context.filesDir, "translation_cache_llm")

    fun get(text: String, lang: String, model: String): String? = read(translationCacheKey(model, lang, text), lang)
    fun put(text: String, lang: String, model: String, value: String) = write(translationCacheKey(model, lang, text), value)
    // A separate cache namespace proves the user explicitly requested regeneration.
    fun getRegenerated(text: String, lang: String, model: String): String? =
        read("regenerated-" + translationCacheKey(model, lang, text), lang)
    fun putRegenerated(text: String, lang: String, model: String, value: String) =
        write("regenerated-" + translationCacheKey(model, lang, text), value)
    fun getMeaning(word: String, sentence: String?, lang: String, model: String): String? =
        read(meaningCacheKey(model, lang, word, sentence), lang)
    fun putMeaning(word: String, sentence: String?, lang: String, model: String, value: String) =
        write(meaningCacheKey(model, lang, word, sentence), value)
    fun remove(text: String, lang: String, model: String) = synchronized(lock) {
        File(dir, "regenerated-${translationCacheKey(model, lang, text)}.txt").delete()
        File(dir, "${translationCacheKey(model, lang, text)}.txt").delete()
    }

    /** Includes live and prepared entries, across all models. User overrides are separate. */
    fun sizeBytes(): Long = synchronized(lock) { dir.listFiles().orEmpty().sumOf { it.length() } }
    fun clearAll() = synchronized(lock) { dir.listFiles().orEmpty().forEach { it.delete() }; Unit }

    private fun read(key: String, lang: String): String? = synchronized(lock) {
        val file = File(dir, "$key.txt")
        if (file.exists()) polishTranslation(file.readText(Charsets.UTF_8), lang).takeIf { it.isNotBlank() } else null
    }

    private fun write(key: String, value: String) = synchronized(lock) {
        dir.mkdirs()
        val target = File(dir, "$key.txt")
        val staging = File.createTempFile(".translation-", ".tmp", dir)
        try {
            staging.writeText(value, Charsets.UTF_8)
            check(staging.renameTo(target)) { "Unable to store translation" }
        } finally { staging.delete() }
    }

    companion object { private val lock = Any() }
}
