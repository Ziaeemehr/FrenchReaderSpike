package com.ziaee.frenchreader.translate

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ExampleTranslationTest {
    private fun forbidden(): Nothing = error("Lower priority provider was called")

    @Test fun bundledBeatsLiveAndGoogleWithoutNetwork() = runBlocking {
        assertEquals("included", resolveExampleTranslation({ null }, { "included" }, { null },
            { forbidden() }, { forbidden() }))
    }
    @Test fun overrideBeatsBundledAndPersonalCache() = runBlocking {
        assertEquals("edited", resolveExampleTranslation({ "edited" }, { forbidden() }, { forbidden() },
            { forbidden() }, { forbidden() }))
    }
    @Test fun explicitlyRegeneratedPersonalCacheBeatsBundled() = runBlocking {
        assertEquals("my model", resolveExampleTranslation({ null }, { forbidden() }, { "my model" },
            { forbidden() }, { forbidden() }))
    }
    @Test fun missesUseLiveThenGoogle() = runBlocking {
        assertEquals("live", resolveExampleTranslation({ null }, { null }, { null }, { "live" }, { forbidden() }))
        assertEquals("google", resolveExampleTranslation({ null }, { null }, { null }, { null }, { "google" }))
    }
}
