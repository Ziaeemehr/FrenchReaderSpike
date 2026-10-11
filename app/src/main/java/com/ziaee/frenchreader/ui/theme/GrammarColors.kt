package com.ziaee.frenchreader.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** [accent] and [accentContainer] are the grammar section's own violet identity (headers, tables, entry cards). */
data class GrammarColors(
    val target: Color, val correct: Color, val wrong: Color, val note: Color,
    val accent: Color, val accentContainer: Color
)
val GrammarLightColors = GrammarColors(
    Color(0xFF7A2432), Color(0xFF246443), Color(0xFFBA1A1A), Color(0xFFF9E3BB),
    accent = Color(0xFF5B3E96), accentContainer = Color(0xFFE9DDFF)
)
val GrammarDarkColors = GrammarColors(
    Color(0xFFFFB8C2), Color(0xFFA0D6B3), Color(0xFFFFB4AB), Color(0xFF5D410D),
    accent = Color(0xFFD3BBFF), accentContainer = Color(0xFF4A3478)
)
val LocalGrammarColors = staticCompositionLocalOf { GrammarLightColors }
