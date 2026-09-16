package com.ziaee.frenchreader.tts

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Synthesizes (or reuses from cache) one text chunk. Retries once on
 * failure (network blip) without touching the user's reading position --
 * per acceptance criterion #7 ("قطع اینترنت ... باعث از دست رفتن متن یا
 * لغات نشود").
 */
class TtsChunkRepository(context: Context) {
    private val cache = TtsCache(context)

    suspend fun getOrSynthesize(
        text: String,
        voice: String,
        ratePercent: Int,
        attempt: Int = 0
    ): Result<SynthesisResult> = withContext(Dispatchers.IO) {
        cache.get(text, voice, ratePercent)?.let { return@withContext Result.success(it) }

        try {
            val outFile = cache.audioPathFor(text, voice, ratePercent)
            val result = PyTts.synthesizeSentences(text, voice, ratePercent, outFile)
            cache.put(text, voice, ratePercent, result)
            Result.success(result)
        } catch (e: Exception) {
            if (attempt < 1) {
                getOrSynthesize(text, voice, ratePercent, attempt + 1)
            } else {
                Result.failure(e)
            }
        }
    }

    fun clearCache() = cache.clearAll()
    fun cacheSizeBytes() = cache.sizeBytes()
}
