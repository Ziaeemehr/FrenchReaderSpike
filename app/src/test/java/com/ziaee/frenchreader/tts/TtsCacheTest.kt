package com.ziaee.frenchreader.tts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TtsCacheTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `max size keeps pinned entry while evicting non-pinned entry`() {
        val cache = newCache()
        putEntry(cache, "pinned")
        putEntry(cache, "ordinary")
        cache.pin("pinned", VOICE, RATE)

        cache.enforceMaxSize(0)

        assertNotNull(cache.get("pinned", VOICE, RATE))
        assertTrue(cache.isPinned("pinned", VOICE, RATE))
        assertFalse(cache.audioPathFor("ordinary", VOICE, RATE).exists())
    }

    @Test
    fun `age pruning deletes old ordinary entry and keeps old pinned entry`() {
        val cache = newCache()
        val pinnedAudio = putEntry(cache, "pinned")
        val ordinaryAudio = putEntry(cache, "ordinary")
        cache.pin("pinned", VOICE, RATE)
        val oldTimestamp = System.currentTimeMillis() - 10_000
        pinnedAudio.setLastModified(oldTimestamp)
        ordinaryAudio.setLastModified(oldTimestamp)

        cache.pruneOlderThan(1_000)

        assertTrue(pinnedAudio.exists())
        assertFalse(ordinaryAudio.exists())
    }

    @Test
    fun `clear all preserves pinned entry and removes ordinary entry`() {
        val cache = newCache()
        val pinnedAudio = putEntry(cache, "pinned")
        val ordinaryAudio = putEntry(cache, "ordinary")
        cache.pin("pinned", VOICE, RATE)

        cache.clearAll()

        assertTrue(pinnedAudio.exists())
        assertTrue(cache.isPinned("pinned", VOICE, RATE))
        assertFalse(ordinaryAudio.exists())
    }

    @Test
    fun `max size evicts least recently used ordinary entry first`() {
        val cache = newCache()
        val olderAudio = putEntry(cache, "older")
        val newerAudio = putEntry(cache, "newer")
        olderAudio.setLastModified(1_000)
        newerAudio.setLastModified(2_000)
        val bytesToKeepNewer = entrySize(cache, "newer")

        cache.enforceMaxSize(bytesToKeepNewer)

        assertFalse(olderAudio.exists())
        assertTrue(newerAudio.exists())
    }

    @Test
    fun `cache hit refreshes recency used by max size eviction`() {
        val cache = newCache()
        val refreshedAudio = putEntry(cache, "refreshed")
        val staleAudio = putEntry(cache, "stale")
        refreshedAudio.setLastModified(1_000)
        staleAudio.setLastModified(2_000)

        assertNotNull(cache.get("refreshed", VOICE, RATE))
        cache.enforceMaxSize(entrySize(cache, "refreshed"))

        assertTrue(refreshedAudio.exists())
        assertFalse(staleAudio.exists())
    }

    private fun newCache(): TtsCache = TtsCache(temporaryFolder.newFolder())

    private fun putEntry(cache: TtsCache, text: String): File {
        val audio = cache.audioPathFor(text, VOICE, RATE)
        audio.writeBytes(ByteArray(16) { it.toByte() })
        cache.put(
            text,
            VOICE,
            RATE,
            SynthesisResult(audio, listOf(SentenceBoundary(text, 0.0, 100.0)))
        )
        return audio
    }

    private fun entrySize(cache: TtsCache, text: String): Long {
        val audio = cache.audioPathFor(text, VOICE, RATE)
        val metadata = File(audio.parentFile, "${audio.nameWithoutExtension}.json")
        return audio.length() + metadata.length()
    }

    companion object {
        private const val VOICE = "fr-FR-DeniseNeural"
        private const val RATE = 0
    }
}
