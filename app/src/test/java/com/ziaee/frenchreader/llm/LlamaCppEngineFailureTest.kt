package com.ziaee.frenchreader.llm

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LlamaCppEngineFailureTest {
    @Test
    fun ensureLoadedReturnsFalseWhenModelFileMissing() = runTest {
        val engine = LlamaCppEngine(
            modelPathProvider = { "/nonexistent/path/model.gguf" },
            isSupportedAbi = { true },
        )
        assertEquals(false, engine.ensureLoaded())
    }

    @Test
    fun generateReturnsNotDownloadedWhenModelFileMissing() = runTest {
        val engine = LlamaCppEngine(
            modelPathProvider = { "/nonexistent/path/model.gguf" },
            isSupportedAbi = { true },
        )
        val result = engine.generate("system", "user", 50)
        assertEquals(LlmResult.Failure(LlmResult.FailureReason.NOT_DOWNLOADED), result)
    }

    @Test
    fun generateReturnsNotDownloadedOnUnsupportedAbi() = runTest {
        val engine = LlamaCppEngine(
            modelPathProvider = { "/nonexistent/path/model.gguf" },
            isSupportedAbi = { false },
        )
        val result = engine.generate("system", "user", 50)
        assertEquals(LlmResult.Failure(LlmResult.FailureReason.NOT_DOWNLOADED), result)
    }
}
