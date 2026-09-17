package com.ziaee.frenchreader.llm

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun generateProducesFrenchSummaryWithoutThinkingLeakage() {
        val modelPath = "/data/local/tmp/llmspike/Qwen_Qwen3-0.6B-Q4_K_M.gguf"
        val handle = LlmNative.nativeLoadModel(modelPath, 2048, 4)
        assertNotEquals(0L, handle)

        val result = LlmNative.nativeGenerate(
            handle,
            "Tu es un assistant pour un apprenant de francais. Reponds uniquement en francais, de facon concise et directe, sans repeter la consigne.",
            "Resume ce texte en 2 phrases maximum, en francais.\n\nTexte:\nLe Mont-Blanc est la plus haute montagne d'Europe occidentale.",
            120,
            null,
        )

        assertTrue("Expected non-empty output", result.isNotBlank())
        assertFalse("Thinking-mode leakage must not appear", result.contains("[Start thinking]"))
        LlmNative.nativeUnload(handle)
    }
}
