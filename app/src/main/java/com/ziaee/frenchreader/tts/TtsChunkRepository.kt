package com.ziaee.frenchreader.tts

import android.content.Context
import com.ziaee.frenchreader.data.TtsCachePrefs
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
            cache.pruneOlderThan(TtsCachePrefs.getMaxAgeDays(context) * 24L * 60 * 60 * 1000)
            cache.enforceMaxSize(TtsCachePrefs.getMaxSizeMb(context) * 1024L * 1024L)
            Result.success(result)
        } catch (e: Exception) {
            if (attempt < 1) {
                getOrSynthesize(text, voice, ratePercent, attempt + 1)
            } else {
                Result.failure(e)
            }
        }
    }

    fun pinText(text: String, voice: String, ratePercent: Int) =
        cache.pin(text, cacheVoice(voice), ratePercent)

    fun unpinText(text: String, voice: String, ratePercent: Int) =
        cache.unpin(text, cacheVoice(voice), ratePercent)

    fun setDocumentPinned(chunkTexts: List<String>, voice: String, ratePercent: Int, pinned: Boolean) {
        chunkTexts.forEach { text ->
            if (pinned) pinText(text, voice, ratePercent) else unpinText(text, voice, ratePercent)
        }
    }

    fun clearCache() = cache.clearAll()
    fun cacheSizeBytes() = cache.sizeBytes()

    private fun cacheVoice(voice: String): String = if (voice.startsWith(XTTS_VOICE_PREFIX)) {
        "$XTTS_VOICE_PREFIX${XttsPrefs.getSpeaker(context)}"
    } else voice
}
