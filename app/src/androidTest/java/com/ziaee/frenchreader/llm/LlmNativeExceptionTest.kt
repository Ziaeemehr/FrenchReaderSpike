package com.ziaee.frenchreader.llm

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LlmNativeExceptionTest {
    @Test
    fun invalidGrammarReturnsEmptyResultInsteadOfEscapingNativeException() {
        val modelPath = "/data/local/tmp/llmspike/Qwen_Qwen3-0.6B-Q4_K_M.gguf"
        val handle = LlmNative.nativeLoadModel(modelPath, 2048, 4)
        assertNotEquals(0L, handle)

        try {
            val result = LlmNative.nativeGenerate(
                handle,
                "Tu es un assistant concis.",
                "Resume cette phrase: Le chat dort.",
                20,
                "root ::= (",
            )

            assertEquals("", result)
        } finally {
            LlmNative.nativeUnload(handle)
        }
    }
}
