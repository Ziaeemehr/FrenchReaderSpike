package com.ziaee.frenchreader.translate.llm

import org.junit.Assert.*
import org.junit.Test

class LlmTest {
    @Test fun linePromptIncludesTargetsAndContext() {
        val prompt = linesPrompt(listOf("Bonjour", "Au revoir"), "Avant", "Après", "fa")
        assertTrue(prompt.system.contains("Persian (Farsi)"))
        assertTrue(prompt.system.contains("ZWNJ"))
        assertTrue(prompt.user.contains("CONTEXT BEFORE: Avant"))
        assertTrue(prompt.user.contains("[\"Bonjour\",\"Au revoir\"]"))
        assertTrue(prompt.user.contains("CONTEXT AFTER: Après"))
    }

    @Test fun emptyContextIsOmitted() {
        val prompt = linesPrompt(listOf("Bonjour"), " ", null, "en")
        assertFalse(prompt.user.contains("CONTEXT BEFORE"))
        assertFalse(prompt.user.contains("CONTEXT AFTER"))
        assertTrue(prompt.system.contains("English"))
        assertFalse(prompt.system.contains("ZWNJ"))
    }

    @Test fun meaningPromptUsesEntryAndSentence() {
        val prompt = meaningPrompt("grossir", "Il va grossir.", "fa")
        assertTrue(prompt.user.contains("ENTRY: grossir"))
        assertTrue(prompt.user.contains("SENTENCE: Il va grossir."))
        assertTrue(prompt.system.contains("مصدر"))
    }

    @Test fun acceptsPlainFencedAndSurroundedJson() {
        val json = "{\"translations\":[\"Hello\"]}"
        for (content in listOf(json, "```json\n$json\n```", "Here is the result: $json Thanks.")) {
            assertEquals(listOf("Hello"), parseTranslations(parseLlmContent(content), 1))
        }
    }

    @Test fun rejectsWrongCount() = badResponse {
        parseTranslations(parseLlmContent("{\"translations\":[\"Hello\"]}"), 2)
    }

    @Test fun rejectsBlankTranslation() = badResponse {
        parseTranslations(parseLlmContent("{\"translations\":[\"  \"]}"), 1)
    }

    @Test fun rejectsNonStringTranslation() = badResponse {
        parseTranslations(parseLlmContent("{\"translations\":[42]}"), 1)
    }

    @Test fun rejectsMissingMeaning() = badResponse { parseMeaning(parseLlmContent("{}")) }
    @Test fun rejectsBlankMeaning() = badResponse { parseMeaning(parseLlmContent("{\"meaning\":\" \"}")) }
    @Test fun parsesMeaning() { assertEquals("capital", parseMeaning(parseLlmContent("{\"meaning\":\"capital\"}"))) }

    @Test fun cacheKeysSeparateModelLanguageAndKind() {
        val key = translationCacheKey("gemma4", "fa", "bonjour")
        assertEquals(64, key.length)
        assertEquals(key, translationCacheKey("gemma4", "fa", "bonjour"))
        assertNotEquals(key, translationCacheKey("other", "fa", "bonjour"))
        assertNotEquals(key, translationCacheKey("gemma4", "en", "bonjour"))
        assertNotEquals(key, meaningCacheKey("gemma4", "fa", "bonjour", null))
        assertNotEquals(meaningCacheKey("gemma4", "fa", "bonjour", null), meaningCacheKey("gemma4", "fa", "bonjour", "Bonjour !"))
    }

    @Test fun providerPresetsAreUsable() {
        LlmProvider.entries.filter { it != LlmProvider.CUSTOM }.forEach {
            assertTrue(it.baseUrl.startsWith("https://"))
            assertTrue(it.defaultModel.isNotBlank())
            assertTrue(it.defaultModel in it.suggestedModels)
        }
    }

    @Test fun bridgeUrlNormalization() {
        for (base in listOf("http://h:8765/llm", "http://h:8765/llm/")) {
            assertEquals("http://h:8765/llm/v1/chat/completions", completionUrl(base, true))
        }
        assertEquals("https://h/api/v1/chat/completions", completionUrl("https://h/api/v1", false))
        LlmProvider.entries.filter { it != LlmProvider.CUSTOM }.forEach {
            assertEquals(it.baseUrl + "/chat/completions", completionUrl(it.baseUrl, false))
        }
    }

    @Test fun computerUrlNormalization() {
        assertEquals("http://127.0.0.1:8080/v1/chat/completions", completionUrl("http://127.0.0.1:8080", true))
        assertEquals("http://127.0.0.1:8080/v1/chat/completions", completionUrl("http://127.0.0.1:8080/v1", true))
        assertEquals("http://127.0.0.1:8080/v1/chat/completions", completionUrl("http://127.0.0.1:8080/v1/", true))
        assertEquals("http://localhost:8080/proxy/v1/chat/completions", completionUrl("http://localhost:8080/proxy", true))
        assertEquals("https://api.deepseek.com/chat/completions", completionUrl("https://api.deepseek.com", false))
    }

    private fun badResponse(block: () -> Any) {
        try {
            block()
            fail("Expected BAD_RESPONSE")
        } catch (error: LlmError) {
            assertEquals(LlmError.Kind.BAD_RESPONSE, error.kind)
        }
    }

    @Test fun onlyAJsonModeRejectionRetriesWithoutIt() {
        assertTrue(rejectsJsonMode("""{"error":{"message":"response_format is not supported by this model"}}"""))
        assertTrue(rejectsJsonMode("json_object mode unavailable"))
        assertFalse(rejectsJsonMode("""{"error":{"message":"max_tokens too large"}}"""))
        assertFalse(rejectsJsonMode(null))
    }

    @Test fun promptAsksToKeepTextAlreadyInTheTargetLanguage() {
        assertTrue(linesPrompt(listOf("Salut"), null, null, "fa").system.contains("already in Persian (Farsi), return it unchanged"))
    }

    @Test fun providerErrorMessageIsReadFromTheBody() {
        assertEquals("This model is not available in your region.",
            providerErrorMessage("""{"error":{"message":"This model is not available in your region.","code":403}}"""))
        assertEquals("Insufficient credits", providerErrorMessage("""{"error":"Insufficient credits"}"""))
        assertNull(providerErrorMessage("<html>bad gateway</html>"))
        assertNull(providerErrorMessage(null))
    }

    @Test fun providerErrorMessageNeverShowsTheKey() {
        val key = "sk-or-v1-0123456789abcdef0123456789abcdef"
        val message = providerErrorMessage("""{"error":{"message":"Invalid key $key provided"}}""", key)!!
        assertFalse(message.contains("0123456789abcdef"))
        assertEquals("Invalid key •••• provided", message)
        assertFalse(providerErrorMessage("""{"error":"bad token sk-proj-AbCdEfGhIjKlMnOp"}""")!!.contains("AbCdEfGh"))
    }
}
