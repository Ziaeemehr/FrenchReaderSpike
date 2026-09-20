package com.ziaee.frenchreader.tts

import android.content.Context
import com.ziaee.frenchreader.data.XttsPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Synthesizes (or reuses from cache) one text chunk. Retries once on
 * failure (network blip) without touching the user's reading position --
 * per acceptance criterion #7 ("قطع اینترنت ... باعث از دست رفتن متن یا
 * لغات نشود").
 */
class TtsChunkRepository(context: Context) {
    private val context = context.applicationContext
    private val cache = TtsCache(context)

    suspend fun getOrSynthesize(
        text: String,
        voice: String,
        ratePercent: Int,
        attempt: Int = 0
    ): Result<SynthesisResult> = withContext(Dispatchers.IO) {
        val cacheVoice = if (voice.startsWith(XTTS_VOICE_PREFIX)) {
            "$XTTS_VOICE_PREFIX${XttsPrefs.getSpeaker(context)}"
        } else voice
        cache.get(text, cacheVoice, ratePercent)?.let { return@withContext Result.success(it) }

        try {
            val outFile = cache.audioPathFor(text, cacheVoice, ratePercent)
            val result = if (voice.startsWith(XTTS_VOICE_PREFIX)) {
                XttsClient.synthesizeSentences(context, text, XttsPrefs.getSpeaker(context), ratePercent, outFile)
            } else {
                PyTts.synthesizeSentences(text, voice, ratePercent, outFile)
            }
            cache.put(text, cacheVoice, ratePercent, result)
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
