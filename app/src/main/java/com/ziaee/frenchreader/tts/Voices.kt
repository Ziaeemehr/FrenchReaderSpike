package com.ziaee.frenchreader.tts

import androidx.annotation.StringRes
import com.ziaee.frenchreader.R

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

val AVAILABLE_VOICES = listOf(
    VoiceOption("fr-FR-HenriNeural", R.string.voice_label_henri),
    VoiceOption("fr-FR-DeniseNeural", R.string.voice_label_denise),
    VoiceOption("fr-FR-EloiseNeural", R.string.voice_label_eloise),
    VoiceOption("fr-CA-SylvieNeural", R.string.voice_label_sylvie),
    VoiceOption("fr-CA-AntoineNeural", R.string.voice_label_antoine),
    VoiceOption("fr-BE-CharlineNeural", R.string.voice_label_charline),
    VoiceOption("fr-BE-GerardNeural", R.string.voice_label_gerard),
    VoiceOption("fr-CH-ArianeNeural", R.string.voice_label_ariane)
)
