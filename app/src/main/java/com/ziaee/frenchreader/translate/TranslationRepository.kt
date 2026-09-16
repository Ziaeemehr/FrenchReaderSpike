package com.ziaee.frenchreader.translate

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TranslationRepository(context: Context) {
    private val cache = TranslationCache(context)

    suspend fun getOrTranslate(
        text: String,
        targetLang: String,
        attempt: Int = 0
    ): Result<String> = withContext(Dispatchers.IO) {
        cache.get(text, targetLang)?.let { return@withContext Result.success(it) }
        try {
            val translated = TranslationService.translate(text, targetLang)
            cache.put(text, targetLang, translated)
            Result.success(translated)
        } catch (e: Exception) {
            if (attempt < 1) getOrTranslate(text, targetLang, attempt + 1)
            else Result.failure(e)
        }
    }
}
