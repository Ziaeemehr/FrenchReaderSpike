package com.ziaee.frenchreader.tts

import androidx.annotation.StringRes
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.language.LanguageCatalog

/**
 * A curated subset of edge-tts's French Neural voices -- enough variety in
 * gender and regional accent (France/Canada/Belgium/Switzerland) to be
 * useful for TCF listening practice, without overwhelming the picker with
 * every voice edge-tts happens to expose. [id] is the exact edge-tts voice
 * name passed straight through to [PyTts.synthesizeSentences]. [labelRes]
 * points at a `values`/`values-fr`/`values-fa` string so the picker shows
 * the voice's name/gender/accent in the app's current UI language rather
 * than a language hardcoded in Kotlin.
 *
 * IMPORTANT: edge-tts only exposes a fixed subset of Microsoft's full Azure
 * Neural voice catalog (the ones wired into Edge's "Read aloud" feature),
 * which is smaller than the full list Azure Cognitive Services offers
 * elsewhere. A voice ID that looks plausible (matches the fr-FR-*Neural
 * naming pattern) but isn't in edge-tts's actual list fails at synthesis
 * time with "NoAudioReceived: No audio was received..." -- this is exactly
 * what happened with fr-FR-JeromeNeural (removed below; edge-tts's fr-FR
 * set is only Denise/Eloise/Henri). Before adding a new entry here, verify
 * it against edge-tts's own `edge-tts --list-voices` output, not just any
 * general Azure voices reference.
 */
data class VoiceOption(val id: String, @StringRes val labelRes: Int)

const val XTTS_VOICE_PREFIX = "xtts:"

private val EDGE_VOICES = listOf(
    VoiceOption("fr-FR-HenriNeural", R.string.voice_label_henri),
    VoiceOption("fr-FR-DeniseNeural", R.string.voice_label_denise),
    VoiceOption("fr-FR-EloiseNeural", R.string.voice_label_eloise),
    VoiceOption("fr-CA-SylvieNeural", R.string.voice_label_sylvie),
    VoiceOption("fr-CA-AntoineNeural", R.string.voice_label_antoine),
    VoiceOption("fr-BE-CharlineNeural", R.string.voice_label_charline),
    VoiceOption("fr-BE-GerardNeural", R.string.voice_label_gerard),
    VoiceOption("fr-CH-ArianeNeural", R.string.voice_label_ariane),
    VoiceOption("de-DE-KatjaNeural", R.string.voice_label_katja),
    VoiceOption("de-DE-ConradNeural", R.string.voice_label_conrad),
    VoiceOption("de-DE-AmalaNeural", R.string.voice_label_amala),
    VoiceOption("de-DE-KillianNeural", R.string.voice_label_killian),
    VoiceOption("de-AT-IngridNeural", R.string.voice_label_ingrid),
    VoiceOption("de-AT-JonasNeural", R.string.voice_label_jonas),
    VoiceOption("de-CH-LeniNeural", R.string.voice_label_leni),
    VoiceOption("de-CH-JanNeural", R.string.voice_label_jan),
)

private val XTTS_VOICE = VoiceOption("${XTTS_VOICE_PREFIX}local", R.string.voice_label_xtts_local)

fun voicesFor(language: String): List<VoiceOption> {
    val support = LanguageCatalog.forCode(language)
    val edge = support.edgeVoiceIds.mapNotNull { id -> EDGE_VOICES.firstOrNull { it.id == id } }
    return if (support.xttsSupported) edge + XTTS_VOICE else edge
}

fun resolveVoice(storedVoice: String, language: String): String {
    val support = LanguageCatalog.forCode(language)
    if (storedVoice.startsWith(XTTS_VOICE_PREFIX) && support.xttsSupported) return storedVoice
    return storedVoice.takeIf { it in support.edgeVoiceIds } ?: support.defaultVoiceId
}
