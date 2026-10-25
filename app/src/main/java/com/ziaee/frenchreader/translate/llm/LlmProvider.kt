package com.ziaee.frenchreader.translate.llm

enum class LlmProvider(val baseUrl: String, val defaultModel: String, val suggestedModels: List<String>) {
    OPENROUTER("https://openrouter.ai/api/v1", "deepseek/deepseek-chat-v3-0324", listOf(
        "deepseek/deepseek-chat-v3-0324", "qwen/qwen3-235b-a22b-2507", "google/gemini-2.5-flash", "google/gemma-3-27b-it"
    )),
    DEEPSEEK("https://api.deepseek.com", "deepseek-chat", listOf("deepseek-chat")),
    QWEN("https://dashscope-intl.aliyuncs.com/compatible-mode/v1", "qwen-plus", listOf("qwen-plus")),
    KIMI("https://api.moonshot.ai/v1", "kimi-k2-0905-preview", listOf("kimi-k2-0905-preview")),
    CUSTOM("", "", emptyList())
}
