package com.ziaee.frenchreader.llm

sealed interface LlmResult {
    data class Success(val text: String) : LlmResult
    data class Failure(val reason: FailureReason) : LlmResult

    enum class FailureReason { NOT_DOWNLOADED, LOAD_FAILED, OUT_OF_MEMORY, TIMEOUT, GENERATION_FAILED }
}

interface LocalLlmEngine {
    suspend fun ensureLoaded(): Boolean
    suspend fun generate(
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int,
        grammar: String? = null,
    ): LlmResult
    fun unload()
}
