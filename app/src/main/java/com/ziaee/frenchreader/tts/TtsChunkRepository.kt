package com.ziaee.frenchreader.tts

import android.content.Context
import com.ziaee.frenchreader.data.TtsCachePrefs
import com.ziaee.frenchreader.data.XttsPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Synthesizes (or reuses from cache) one text chunk. Retries once on
 * failure (network blip) without touching the user's reading position --
 * per acceptance criterion #7 ("قطع اینترنت ... باعث از دست رفتن متن یا
 * لغات نشود").
 */
private const val MAX_ATTEMPTS = 2

/** A per-audio-file lock that is dropped again once nobody holds or waits for it. */
private class SynthesisLock {
    val mutex = Mutex()
    var users = 0
}

private val synthesisLocks = ConcurrentHashMap<String, SynthesisLock>()

private suspend inline fun <T> withSynthesisLock(path: String, block: () -> T): T {
    val lock = synthesisLocks.compute(path) { _, existing -> (existing ?: SynthesisLock()).also { it.users++ } }!!
    try {
        return lock.mutex.withLock { block() }
    } finally {
        synthesisLocks.compute(path) { _, existing -> existing?.takeIf { --it.users > 0 } }
    }
}

class TtsChunkRepository(context: Context) {
    private val context = context.applicationContext
    private val cache = TtsCache(context)

    suspend fun getOrSynthesize(
        text: String,
        voice: String,
        ratePercent: Int,
        onPartial: (suspend (PartialSynthesis) -> Unit)? = null
    ): Result<SynthesisResult> = withContext(Dispatchers.IO) {
        val cacheVoice = cacheVoice(voice)
        cache.get(text, cacheVoice, ratePercent)?.let { return@withContext Result.success(it) }
        val outFile = cache.audioPathFor(text, cacheVoice, ratePercent)

        // One synthesis per audio file at a time: a second request for the same
        // text waits for the first and then finds it in the cache, instead of two
        // writers interleaving in one file.
        withSynthesisLock(outFile.path) {
            cache.get(text, cacheVoice, ratePercent)?.let { return@withContext Result.success(it) }
            var lastError: Exception = IllegalStateException("Synthesis did not run")
            repeat(MAX_ATTEMPTS) {
                var partialDelivered = false
                try {
                    val result = if (voice.startsWith(XTTS_VOICE_PREFIX)) {
                        synthesizeWithXtts(text, ratePercent, outFile, onPartial) { partialDelivered = true }
                    } else {
                        PyTts.synthesizeSentences(text, voice, ratePercent, outFile)
                    }
                    cache.put(text, cacheVoice, ratePercent, result)
                    cache.pruneOlderThan(TtsCachePrefs.getMaxAgeDays(context) * 24L * 60 * 60 * 1000)
                    cache.enforceMaxSize(TtsCachePrefs.getMaxSizeMb(context) * 1024L * 1024L)
                    return@withContext Result.success(result)
                } catch (e: Exception) {
                    lastError = e
                    // Once part of the audio has been handed to the player a silent
                    // retry would queue it twice, and a caller that no longer wants
                    // the result must not trigger one either: report the failure.
                    if (partialDelivered || e is CancellationException) {
                        outFile.delete()
                        return@withContext Result.failure(e)
                    }
                }
            }
            Result.failure(lastError)
        }
    }

    /**
     * With [onPartial] the server is asked to stream sentence by sentence, so the
     * player can start on the first sentence while the rest of the paragraph is
     * still being generated; an older server without that endpoint falls back to
     * the all-at-once request.
     */
    private fun synthesizeWithXtts(
        text: String,
        ratePercent: Int,
        outFile: File,
        onPartial: (suspend (PartialSynthesis) -> Unit)?,
        onPartialDelivered: () -> Unit
    ): SynthesisResult {
        val speaker = XttsPrefs.getSpeaker(context)
        val streamed = if (onPartial == null) null else {
            GrowingAudioFiles.begin(outFile)
            try {
                XttsClient.synthesizeSentencesStreaming(context, text, speaker, ratePercent, outFile) { received, planned ->
                    onPartialDelivered()
                    runBlocking {
                        withContext(Dispatchers.Main) { onPartial(PartialSynthesis(outFile, received, planned)) }
                    }
                }
            } finally {
                GrowingAudioFiles.finish(outFile)
            }
        }
        return streamed ?: XttsClient.synthesizeSentences(context, text, speaker, ratePercent, outFile)
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
