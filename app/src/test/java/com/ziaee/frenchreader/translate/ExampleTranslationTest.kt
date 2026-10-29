package com.ziaee.frenchreader.translate

import com.ziaee.frenchreader.translate.llm.LlmError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ExampleTranslationTest {
    private fun forbidden(): Nothing = error("Lower priority provider was called")

    @Test fun bundledBeatsLiveAndGoogleWithoutNetwork() = runBlocking {
        assertEquals(ExampleTranslation("included", ExampleSource.BUNDLED), resolveExampleTranslation({ null }, { "included" }, { null },
            { forbidden() }, { forbidden() }))
    }
    @Test fun overrideBeatsBundledAndPersonalCache() = runBlocking {
        assertEquals(ExampleTranslation("edited", ExampleSource.EDITED), resolveExampleTranslation({ "edited" }, { forbidden() }, { forbidden() },
            { forbidden() }, { forbidden() }))
    }
    @Test fun explicitlyRegeneratedPersonalCacheBeatsBundled() = runBlocking {
        assertEquals(ExampleTranslation("my model", ExampleSource.MODEL), resolveExampleTranslation({ null }, { forbidden() }, { "my model" },
            { forbidden() }, { forbidden() }))
    }
    @Test fun failuresExposeReason() = runBlocking {
        val result = resolveExampleTranslation({ null }, { null }, { null },
            { throw java.io.IOException() }, { "fallback" })
        assertEquals(ExampleTranslation("fallback", ExampleSource.GOOGLE,
            LlmError.Kind.UNREACHABLE), result)
        val bad = resolveExampleTranslation({ null }, { null }, { null },
            { error("bad") }, { "fallback" })
        assertEquals(LlmError.Kind.BAD_RESPONSE, bad.modelError)
    }
    @Test fun forceSkipsLowerProvidersAndPersonal() = runBlocking {
        assertEquals(ExampleTranslation("fresh", ExampleSource.MODEL),
            resolveExampleTranslation({ null }, { forbidden() }, { forbidden() }, { "fresh" }, { forbidden() }, true))
        assertEquals(ExampleTranslation("edited", ExampleSource.EDITED),
            resolveExampleTranslation({ "edited" }, { forbidden() }, { forbidden() }, { forbidden() }, { forbidden() }, true))
        try {
            resolveExampleTranslation({ null }, { forbidden() }, { forbidden() },
                { throw java.io.IOException() }, { forbidden() }, true)
            fail("Expected model failure")
        } catch (error: LlmError) {
            assertEquals(LlmError.Kind.UNREACHABLE, error.kind)
        }
    }
    @Test fun modelErrorKindsArePreserved() = runBlocking {
        for (kind in LlmError.Kind.values()) {
            val failure = LlmError(kind, "provider detail")
            val result = resolveExampleTranslation({ null }, { null }, { null },
                { throw LlmError(kind) }, { "google" })
            assertEquals(ExampleTranslation("google", ExampleSource.GOOGLE, kind), result)
            try {
                resolveExampleTranslation({ null }, { forbidden() }, { forbidden() },
                    { throw failure }, { forbidden() }, true)
                fail("Expected model failure")
            } catch (error: LlmError) {
                assertSame(failure, error)
            }
        }
    }
    @Test fun forceWithoutModelFailsWithoutGoogle() = runBlocking {
        try {
            resolveExampleTranslation({ null }, { forbidden() }, { forbidden() }, { null }, { forbidden() }, true)
            fail("Expected model failure")
        } catch (error: LlmError) {
            assertEquals(LlmError.Kind.UNREACHABLE, error.kind)
        }
    }
    @Test fun cancellationIsRethrown() = runBlocking {
        for (forceModel in listOf(false, true)) {
            try {
                resolveExampleTranslation({ null }, { null }, { null },
                    { throw CancellationException() }, { forbidden() }, forceModel)
                fail("Expected cancellation")
            } catch (_: CancellationException) { }
        }
    }
    @Test fun lazyProvidersPreserveCancellation() = runBlocking {
        for (provider in 0..2) {
            try {
                resolveExampleTranslation(
                    { if (provider == 0) throw CancellationException() else null },
                    { if (provider == 2) throw CancellationException() else null },
                    { if (provider == 1) throw CancellationException() else null },
                    { forbidden() }, { forbidden() })
                fail("Expected cancellation")
            } catch (_: CancellationException) { }
        }
    }
    @Test fun overrideSavedDuringModelRequestWins() = runBlocking {
        for (forceModel in listOf(false, true)) {
            for (fails in listOf(false, true)) {
                var edited: String? = null
                val result = resolveExampleTranslation({ edited }, { null }, { null }, {
                    edited = "new edit"
                    if (fails) throw LlmError(LlmError.Kind.RATE_LIMIT)
                    "model"
                }, { forbidden() }, forceModel)
                assertEquals(ExampleTranslation("new edit", ExampleSource.EDITED), result)
            }
        }
    }
    @Test fun overrideSavedDuringGoogleRequestWins() = runBlocking {
        for (fails in listOf(false, true)) {
            var edited: String? = null
            val result = resolveExampleTranslation({ edited }, { null }, { null }, { null }, {
                edited = "new edit"
                if (fails) throw java.io.IOException()
                "google"
            })
            assertEquals(ExampleTranslation("new edit", ExampleSource.EDITED), result)
        }
    }
    @Test fun noModelUsesGoogleWithoutModelError() = runBlocking {
        val result = resolveExampleTranslation({ null }, { null }, { null }, { null }, { "google" })
        assertEquals(ExampleSource.GOOGLE, result.source)
        assertNull(result.modelError)
    }
    @Test fun missesUseLiveThenGoogle() = runBlocking {
        assertEquals(ExampleTranslation("live", ExampleSource.MODEL), resolveExampleTranslation({ null }, { null }, { null }, { "live" }, { forbidden() }))
        assertEquals(ExampleTranslation("google", ExampleSource.GOOGLE), resolveExampleTranslation({ null }, { null }, { null }, { null }, { "google" }))
    }
}
