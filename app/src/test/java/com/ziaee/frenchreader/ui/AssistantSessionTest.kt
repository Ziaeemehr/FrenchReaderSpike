package com.ziaee.frenchreader.ui

import com.ziaee.frenchreader.llm.LlmResult
import com.ziaee.frenchreader.llm.LocalLlmEngine
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class AssistantSessionTest {
    @Test
    fun dismissalCancelsWorkClearsResultsAndUnloads() = runTest {
        val engine = ControllableEngine()
        val session = AssistantSession(engine, backgroundScope)

        session.run(AssistantAction.SUMMARIZE, "First sentence.")
        runCurrent()
        assertTrue(session.loading)

        session.cancelAndClear()

        assertFalse(session.loading)
        assertNull(session.textResult)
        assertTrue(session.vocabResult.isEmpty())
        assertNull(session.errorReason)
        assertEquals(1, engine.unloadCalls)

        // Native generation is blocking and cannot be interrupted mid-call. Its late
        // completion must not restore a result after the sheet was dismissed.
        engine.complete(value = LlmResult.Success("Stale summary."))
        advanceUntilIdle()
        assertNull(session.textResult)
        assertFalse(session.loading)
    }

    @Test
    fun ignoresSecondActionWhileFirstGenerationIsActive() = runTest {
        val engine = ControllableEngine()
        val session = AssistantSession(engine, backgroundScope)

        session.run(AssistantAction.SUMMARIZE, "One sentence.")
        runCurrent()
        session.run(AssistantAction.GRAMMAR, "Another sentence.")
        runCurrent()

        assertEquals(1, engine.generateCalls)
        assertTrue(session.loading)

        engine.complete(value = LlmResult.Success("A summary."))
        advanceUntilIdle()
        runCurrent()
        advanceUntilIdle()

        assertFalse(session.loading)
        assertEquals("A summary.", session.textResult)
    }

    @Test
    fun newSentenceCannotShowThePreviousSessionsLateResult() = runTest {
        val engine = ControllableEngine()
        val session = AssistantSession(engine, backgroundScope)

        session.run(AssistantAction.SUMMARIZE, "First sentence.")
        runCurrent()
        session.cancelAndClear()
        session.run(AssistantAction.GRAMMAR, "Second sentence.")
        runCurrent()

        engine.complete(index = 0, value = LlmResult.Success("Stale first result."))
        advanceUntilIdle()
        assertTrue(session.loading)
        assertNull(session.textResult)

        engine.complete(index = 1, value = LlmResult.Success("Second result."))
        advanceUntilIdle()
        runCurrent()
        advanceUntilIdle()
        assertFalse(session.loading)
        assertEquals("Second result.", session.textResult)
    }

    private class ControllableEngine : LocalLlmEngine {
        private val continuations = mutableListOf<Continuation<LlmResult>>()
        var generateCalls = 0
        var unloadCalls = 0

        override suspend fun ensureLoaded() = true

        override suspend fun generate(
            systemPrompt: String,
            userPrompt: String,
            maxTokens: Int,
            grammar: String?,
        ): LlmResult {
            generateCalls += 1
            return suspendCoroutine { continuations += it }
        }

        override fun unload() {
            unloadCalls += 1
        }

        fun complete(index: Int = 0, value: LlmResult) {
            continuations[index].resume(value)
        }
    }
}
