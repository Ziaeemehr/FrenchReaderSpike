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
    fun getMeaning(word: String, sentence: String?, lang: String, model: String): String? =
        read(meaningCacheKey(model, lang, word, sentence), lang)
    fun putMeaning(word: String, sentence: String?, lang: String, model: String, value: String) =
        write(meaningCacheKey(model, lang, word, sentence), value)
    fun remove(text: String, lang: String, model: String) {
        File(dir, "${translationCacheKey(model, lang, text)}.txt").delete()
    }

    private fun read(key: String, lang: String): String? {
        val file = File(dir, "$key.txt")
        return if (file.exists()) polishTranslation(file.readText(Charsets.UTF_8), lang).takeIf { it.isNotBlank() } else null
    }

    private fun write(key: String, value: String) {
        dir.mkdirs()
        File(dir, "$key.txt").writeText(value, Charsets.UTF_8)
    }
}
