package com.ziaee.frenchreader.tts

/**
 * A curated subset of edge-tts's French Neural voices -- enough variety in
 * gender and regional accent (France/Canada/Belgium/Switzerland) to be
 * useful for TCF listening practice, without overwhelming the picker with
 * every voice edge-tts happens to expose. [id] is the exact edge-tts voice
 * name passed straight through to [PyTts.synthesizeSentences].
 */
data class VoiceOption(val id: String, val label: String)

val AVAILABLE_VOICES = listOf(
    VoiceOption("fr-FR-DeniseNeural", "دنیز – زن، فرانسه (پیش‌فرض)"),
    VoiceOption("fr-FR-HenriNeural", "هانری – مرد، فرانسه"),
    VoiceOption("fr-FR-EloiseNeural", "الوئیز – زن، فرانسه"),
    VoiceOption("fr-FR-JeromeNeural", "ژروم – مرد، فرانسه"),
    VoiceOption("fr-CA-SylvieNeural", "سیلوی – زن، کانادا"),
    VoiceOption("fr-CA-AntoineNeural", "آنتوان – مرد، کانادا"),
    VoiceOption("fr-BE-CharlineNeural", "شارلین – زن، بلژیک"),
    VoiceOption("fr-CH-ArianeNeural", "آریان – زن، سوئیس")
)

fun voiceLabel(id: String): String = AVAILABLE_VOICES.find { it.id == id }?.label ?: id
