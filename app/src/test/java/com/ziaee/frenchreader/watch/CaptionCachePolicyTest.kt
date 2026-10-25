package com.ziaee.frenchreader.watch

import org.junit.Assert.*
import org.junit.Test

class CaptionCachePolicyTest {
    private val old = VideoCaptions("Imported", listOf(SubtitleCue(0, 1000, "Original.")), "Channel")
    private val fresh = VideoCaptions("YouTube", listOf(SubtitleCue(0, 500, "Fresh.")))

    @Test fun currentEntriesAreUsedAsIsRegardlessOfSource() {
        for (source in listOf("youtube", "file")) {
            val cached = CachedCaptions(old, 2, source)
            assertFalse(shouldFetchCaptions(cached))
            assertEquals(CaptionCacheDecision(old, source, false), chooseCaptions(cached, fresh))
        }
    }

    @Test fun legacyEntriesAreNeverReplacedByYoutubeCaptions() {
        val cached = CachedCaptions(old, null, null)
        assertFalse(shouldFetchCaptions(cached))
        assertEquals(CaptionCacheDecision(old, "file", true), chooseCaptions(cached, fresh))
    }

    @Test fun legacyEntriesPreserveImportedCuesOnEmptyOrFailedFetch() {
        val cached = CachedCaptions(old, null, null)
        for (fetched in listOf(null, fresh.copy(cues = emptyList()))) {
            assertEquals(CaptionCacheDecision(old, "file", true), chooseCaptions(cached, fetched))
        }
    }

    @Test fun uncachedFetchOnlyWritesNonemptyCaptions() {
        assertTrue(shouldFetchCaptions(null))
        assertEquals(CaptionCacheDecision(fresh, "youtube", true), chooseCaptions(null, fresh))
        assertEquals(null, chooseCaptions(null, null))
        val empty = fresh.copy(cues = emptyList())
        assertEquals(CaptionCacheDecision(empty, "youtube", false), chooseCaptions(null, empty))
    }
}
