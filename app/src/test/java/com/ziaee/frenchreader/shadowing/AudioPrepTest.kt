package com.ziaee.frenchreader.shadowing

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioPrepTest {
    @Test fun quietRecordingIsBoostedToTargetPeak() {
        // Real phone level: peak ~2000 of 32767.
        val out = normalizeGain(shortArrayOf(0, 1000, -2000, 500))
        assertEquals(16_000, out.maxOf { kotlin.math.abs(it.toInt()) })
        assertEquals(8_000, out[1].toInt())
    }

    @Test fun gainIsCappedSoNearSilenceIsNotBlownUp() {
        val out = normalizeGain(shortArrayOf(100, -50))
        assertArrayEquals(shortArrayOf(800, -400), out) // max 8x
    }

    @Test fun alreadyLoudRecordingIsUntouched() {
        val pcm = shortArrayOf(20_000, -30_000)
        assertArrayEquals(pcm, normalizeGain(pcm))
    }

    @Test fun silenceAndEmptyAreSafe() {
        assertArrayEquals(ShortArray(3), normalizeGain(ShortArray(3)))
        assertEquals(0, normalizeGain(ShortArray(0)).size)
    }

    @Test fun pacedWriteSendsEverythingInOrderInChunks() = runTest {
        val bytes = ByteArray(10_000) { it.toByte() }
        val chunks = mutableListOf<ByteArray>()
        writePaced(bytes, chunkBytes = 3_200, pauseMs = 25) { chunks += it }
        assertEquals(listOf(3_200, 3_200, 3_200, 400), chunks.map { it.size })
        assertArrayEquals(bytes, chunks.reduce { a, b -> a + b })
        assertTrue(testScheduler.currentTime >= 75) // paused between chunks
    }
}
