package com.ziaee.frenchreader.llm

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LlmNativeSanityTest {
    @Test
    fun nativeLibraryLoadsAndReturnsExpectedValue() {
        assertEquals(42, LlmNative.nativeSanityCheck())
    }

    @Test
    fun loadModelFromSpikeArtifactAndUnload() {
        // Pushed once by the spike; retained per quantitative-results.md. If this file is
        // missing, re-push it: `adb push Qwen_Qwen3-0.6B-Q4_K_M.gguf /data/local/tmp/llmspike/`
        val modelPath = "/data/local/tmp/llmspike/Qwen_Qwen3-0.6B-Q4_K_M.gguf"
        val handle = LlmNative.nativeLoadModel(modelPath, 2048, 4)
        assertNotEquals("Expected a non-zero handle for a valid model file", 0L, handle)
        LlmNative.nativeUnload(handle)
    }
}
