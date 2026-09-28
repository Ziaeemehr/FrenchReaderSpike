package com.ziaee.frenchreader.translate

import android.content.Context
import com.ziaee.frenchreader.text.isRtlText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TranslationRepository(context: Context) {
    private val cache = TranslationCache(context)

    suspend fun getOrTranslate(
        text: String,
        sourceLang: String,
        targetLang: String,
        attempt: Int = 0
    ): Result<String> = withContext(Dispatchers.IO) {
        cache.get(text, sourceLang, targetLang)?.let { return@withContext Result.success(it) }
        try {
            val translated = TranslationService.translate(text, targetLang, sourceLang)
            cache.put(text, sourceLang, targetLang, translated)
            Result.success(translated)
        } catch (e: Exception) {
            if (attempt < 1) getOrTranslate(text, sourceLang, targetLang, attempt + 1)
            else Result.failure(e)
        }
    }

    /**
     * Paragraph translation for the reader: null when the paragraph is already in
     * [targetLang], so it isn't shown translated into itself. A same-language result is
     * cached as the source text itself, and older cached echoes read back as null too.
     */
    suspend fun getOrTranslateParagraph(
        text: String,
        sourceLang: String,
        targetLang: String,
        attempt: Int = 0
    ): Result<String?> = withContext(Dispatchers.IO) {
        if (isRtlTarget(targetLang) && isRtlText(text)) return@withContext Result.success(null)
        cache.get(text, sourceLang, targetLang)?.let { cached ->
            return@withContext Result.success(cached.takeUnless { TranslationService.isSameText(it, text) })
        }
        try {
            val translated = TranslationService.translateUnlessSameLanguage(text, targetLang, sourceLang)
            cache.put(text, sourceLang, targetLang, translated ?: text)
            Result.success(translated)
        } catch (e: Exception) {
            if (attempt < 1) getOrTranslateParagraph(text, sourceLang, targetLang, attempt + 1)
            else Result.failure(e)
        }
    }

    private fun isRtlTarget(targetLang: String) =
        TranslationService.baseLanguage(targetLang) in setOf("fa", "ar", "he", "iw", "ur", "ps")
}
