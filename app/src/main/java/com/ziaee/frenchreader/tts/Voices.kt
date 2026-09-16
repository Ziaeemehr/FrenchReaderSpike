package com.ziaee.frenchreader.tts

/**
 * A curated subset of edge-tts's French Neural voices -- enough variety in
 * gender and regional accent (France/Canada/Belgium/Switzerland) to be
 * useful for TCF listening practice, without overwhelming the picker with
 * every voice edge-tts happens to expose. [id] is the exact edge-tts voice
 * name passed straight through to [PyTts.synthesizeSentences].
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
data class VoiceOption(val id: String, val label: String)

val AVAILABLE_VOICES = listOf(
    VoiceOption("fr-FR-DeniseNeural", "دنیز – زن، فرانسه (پیش‌فرض)"),
    VoiceOption("fr-FR-HenriNeural", "هانری – مرد، فرانسه"),
    VoiceOption("fr-FR-EloiseNeural", "الوئیز – زن، فرانسه"),
    VoiceOption("fr-CA-SylvieNeural", "سیلوی – زن، کانادا"),
    VoiceOption("fr-CA-AntoineNeural", "آنتوان – مرد، کانادا"),
    VoiceOption("fr-BE-CharlineNeural", "شارلین – زن، بلژیک"),
    VoiceOption("fr-BE-GerardNeural", "ژرار – مرد، بلژیک"),
    VoiceOption("fr-CH-ArianeNeural", "آریان – زن، سوئیس")
)

fun voiceLabel(id: String): String = AVAILABLE_VOICES.find { it.id == id }?.label ?: id
