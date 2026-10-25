package com.ziaee.frenchreader.translate.llm

import android.content.Context
import com.ziaee.frenchreader.translate.polishMeaning
import com.ziaee.frenchreader.translate.polishTranslation
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal data class LlmPrompt(val system: String, val user: String)

internal fun languageName(targetLang: String): String = when (targetLang.substringBefore('-').substringBefore('_').lowercase(Locale.ROOT)) {
    "fa" -> "Persian (Farsi)"
    "en" -> "English"
    "fr" -> "French"
    else -> Locale.forLanguageTag(targetLang.replace('_', '-')).getDisplayLanguage(Locale.ENGLISH)
}

internal fun linesPrompt(targets: List<String>, before: String?, after: String?, targetLang: String): LlmPrompt {
    val lang = languageName(targetLang)
    val persian = if (lang == "Persian (Farsi)") "Use standard Persian spelling with half-spaces (ZWNJ). " else ""
    return LlmPrompt(
        "You translate French into natural, fluent $lang for a learner of French. " +
            "Translate only the TARGETS. Use the context only to understand them. Subtitles of speech can be " +
            "fragments or contain transcription mistakes: translate what the speaker means, keeping a fragment a fragment. " +
            persian + "If a target is already in $lang, return it unchanged. " +
            "Reply with JSON only: {\"translations\": [ ... ]}, with exactly one string per target, in the same order.",
        listOfNotNull(
            before?.takeIf { it.isNotBlank() }?.let { "CONTEXT BEFORE: $it" },
            "TARGETS (JSON array): ${JSONArray(targets)}",
            after?.takeIf { it.isNotBlank() }?.let { "CONTEXT AFTER: $it" }
        ).joinToString("\n")
    )
}

internal fun meaningPrompt(word: String, sentence: String?, targetLang: String): LlmPrompt {
    val lang = languageName(targetLang)
    val persian = if (lang == "Persian (Farsi)")
        "For Persian, write verbs and verb phrases as the infinitive (مصدر), and nouns without «یک». " +
            "Use standard Persian spelling with half-spaces (ZWNJ). " else ""
    return LlmPrompt(
        "You are a French-to-$lang dictionary for learners. Give the $lang meaning of the French entry " +
            "as it is used in the given sentence, written like a dictionary headword. " + persian +
            "Use 1 to 4 words, no explanation. Reply with JSON only: {\"meaning\": \"...\"}.",
        listOfNotNull("ENTRY: $word", sentence?.takeIf { it.isNotBlank() }?.let { "SENTENCE: $it" }).joinToString("\n")
    )
}

internal fun parseTranslations(response: JSONObject, count: Int): List<String> {
    val array = response.optJSONArray("translations") ?: throw LlmError(LlmError.Kind.BAD_RESPONSE)
    if (array.length() != count) throw LlmError(LlmError.Kind.BAD_RESPONSE)
    return (0 until array.length()).map { index ->
        (array.opt(index) as? String)?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw LlmError(LlmError.Kind.BAD_RESPONSE)
    }
}

internal fun parseMeaning(response: JSONObject): String =
    (response.opt("meaning") as? String)?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw LlmError(LlmError.Kind.BAD_RESPONSE)

/** [cacheId] names the engine, endpoint and model, so another server or model never reuses these results. */
class LlmTranslator(private val client: LlmClient, val cacheId: String) {
    fun translateLines(targets: List<String>, before: String?, after: String?, targetLang: String): List<String> {
        if (targets.isEmpty()) return emptyList()
        return parseTranslations(client.complete(linesPrompt(targets, before, after, targetLang)), targets.size)
            .map { polishTranslation(it, targetLang) }
    }

    fun meaning(word: String, sentence: String?, targetLang: String): String =
        polishMeaning(word, parseMeaning(client.complete(meaningPrompt(word, sentence, targetLang))), targetLang)

    companion object {
        fun forContext(context: Context): LlmTranslator? {
            val prefs = LlmPrefs(context)
            val engine = prefs.engine
            if (engine == TranslationEngine.GOOGLE) return null
            val computer = engine == TranslationEngine.COMPUTER
            if (!computer && !prefs.experimentalAccepted) return null
            val provider = prefs.onlineProvider
            val baseUrl = if (computer) prefs.computerUrl else if (provider == LlmProvider.CUSTOM) prefs.customBaseUrl else provider.baseUrl
            if (baseUrl.isBlank()) return null
            val secrets = LlmSecrets(context)
            val token = secrets.get(if (computer) "computer_token" else provider.name)
            if (!computer && token.isNullOrBlank()) return null
            val model = if (computer) prefs.computerModel else prefs.onlineModel
            return LlmTranslator(LlmClient(baseUrl, model, token, computer, provider.takeUnless { computer }),
                "${engine.name}|${baseUrl.trim().trimEnd('/')}|$model")
        }
    }
}
