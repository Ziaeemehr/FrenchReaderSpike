package com.ziaee.frenchreader.language

import java.util.Locale

data class LanguageSupport(
    val code: String,
    val locale: Locale,
    val sttLocale: String,
    val edgeVoiceIds: List<String>,
    val defaultVoiceId: String,
    val xttsSupported: Boolean,
    val features: Set<LanguageFeature>,
)

enum class LanguageFeature {
    NEWS,
    VIKIDIA,
    WIKISOURCE,
    SHADOWING,
    FRENCH_ELISION,
}

object LanguageCatalog {
    const val DEFAULT_TARGET = "fr"

    val knownLanguages: List<String> = listOf("fa", "en", "fr", "de")

    val targets: List<LanguageSupport> = listOf(
        LanguageSupport(
            code = "fr",
            locale = Locale.FRENCH,
            sttLocale = "fr-FR",
            edgeVoiceIds = listOf(
                "fr-FR-HenriNeural",
                "fr-FR-DeniseNeural",
                "fr-FR-EloiseNeural",
                "fr-CA-SylvieNeural",
                "fr-CA-AntoineNeural",
                "fr-BE-CharlineNeural",
                "fr-BE-GerardNeural",
                "fr-CH-ArianeNeural",
            ),
            defaultVoiceId = "fr-FR-HenriNeural",
            xttsSupported = true,
            features = setOf(
                LanguageFeature.NEWS,
                LanguageFeature.VIKIDIA,
                LanguageFeature.WIKISOURCE,
                LanguageFeature.SHADOWING,
                LanguageFeature.FRENCH_ELISION,
            ),
        ),
        LanguageSupport(
            code = "de",
            locale = Locale.GERMAN,
            sttLocale = "de-DE",
            edgeVoiceIds = listOf(
                "de-DE-KatjaNeural",
                "de-DE-ConradNeural",
                "de-DE-AmalaNeural",
                "de-DE-KillianNeural",
                "de-AT-IngridNeural",
                "de-AT-JonasNeural",
                "de-CH-LeniNeural",
                "de-CH-JanNeural",
            ),
            defaultVoiceId = "de-DE-KatjaNeural",
            xttsSupported = true,
            features = setOf(
                LanguageFeature.VIKIDIA,
                LanguageFeature.WIKISOURCE,
                LanguageFeature.SHADOWING,
            ),
        ),
    )

    fun forCode(code: String?): LanguageSupport =
        targets.firstOrNull { it.code == code }
            ?: targets.first { it.code == DEFAULT_TARGET }

    fun isSupportedTarget(code: String?): Boolean = targets.any { it.code == code }
}
