package com.ziaee.frenchreader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration

enum class ThemeMode { SYSTEM, LIGHT, DARK }

internal fun resolveDarkTheme(mode: ThemeMode, systemInDarkTheme: Boolean): Boolean = when (mode) {
    ThemeMode.SYSTEM -> systemInDarkTheme
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** The app-wide theme: colors follow [themeMode] (light/dark/system) alone. [ReadingScreen]
 * intentionally does NOT source its colors from this -- it reads [AppearanceState.readingBackground]
 * directly via [readingPaletteFor] for its own Scaffold/text colors, so a reading-background choice
 * only ever affects the reading page, never fights with the light/dark theme everywhere else. */
@Composable
fun FrenchReaderTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit
) {
    val darkTheme = resolveDarkTheme(themeMode, isSystemInDarkTheme())
    val language = LocalConfiguration.current.locales[0].language
    val appScheme = if (darkTheme) FrenchReaderDarkColorScheme else FrenchReaderLightColorScheme
    CompositionLocalProvider(
        LocalAppSpacing provides AppSpacing(),
        LocalAppSizes provides AppSizes(),
        LocalAppElevations provides AppElevations(),
        LocalEditorialTypography provides editorialTypography(language)
    ) {
        MaterialTheme(
            colorScheme = appScheme,
            typography = interfaceTypography(language),
            shapes = FrenchReaderShapes,
            content = content
        )
    }
}

/** Existing names are retained so stored preferences from older versions remain valid. */
enum class ReadingBackground {
    WHITE, SEPIA, PAPER, SAND, GRAY, MINT, SAGE, BLUE_TINT, ROSE, DARK, BLACK
}

enum class HighlightColor { YELLOW, GREEN, BLUE, PINK, ORANGE, PURPLE, TEAL, RED }

data class ReadingPalette(
    val background: Color,
    val ink: Color,
    val inkFaded: Color,
    val highlightBg: Color,
    val highlightInk: Color,
    val divider: Color,
    val accent: Color,
    val selectionBg: Color,
    val selectionHandle: Color
)

fun backgroundSwatch(background: ReadingBackground): Color = when (background) {
    ReadingBackground.WHITE -> Color(0xFFFFFFFF)
    ReadingBackground.SEPIA -> Color(0xFFFBF6EC)
    ReadingBackground.PAPER -> Color(0xFFFFFDF5)
    ReadingBackground.SAND -> Color(0xFFF3E5C8)
    ReadingBackground.GRAY -> Color(0xFFE8E9EB)
    ReadingBackground.MINT -> Color(0xFFE5F3EA)
    ReadingBackground.SAGE -> Color(0xFFDCE7DA)
    ReadingBackground.BLUE_TINT -> Color(0xFFE5EFF8)
    ReadingBackground.ROSE -> Color(0xFFF7E9EA)
    ReadingBackground.DARK -> Color(0xFF1A1A1A)
    ReadingBackground.BLACK -> Color(0xFF0D0D0E)
}

fun highlightSwatch(color: HighlightColor, dark: Boolean = false): Color = when (color) {
    HighlightColor.YELLOW -> if (dark) Color(0xFF65562D) else Color(0xFFF2D995)
    HighlightColor.GREEN -> if (dark) Color(0xFF315A3C) else Color(0xFFAEDDB5)
    HighlightColor.BLUE -> if (dark) Color(0xFF31516B) else Color(0xFFAED5F2)
    HighlightColor.PINK -> if (dark) Color(0xFF704052) else Color(0xFFF2BCD0)
    HighlightColor.ORANGE -> if (dark) Color(0xFF74472A) else Color(0xFFF3C18E)
    HighlightColor.PURPLE -> if (dark) Color(0xFF564371) else Color(0xFFD1B9ED)
    HighlightColor.TEAL -> if (dark) Color(0xFF275D5B) else Color(0xFFA6DEDA)
    HighlightColor.RED -> if (dark) Color(0xFF713838) else Color(0xFFF1AEAA)
}

fun readingPaletteFor(
    background: ReadingBackground,
    highlightColor: HighlightColor = HighlightColor.YELLOW
): ReadingPalette {
    val bg = backgroundSwatch(background)
    val dark = bg.luminance() < 0.35f
    val ink = when (background) {
        ReadingBackground.SEPIA -> Color(0xFF2E2A22)
        ReadingBackground.WHITE -> Color(0xFF1A1A1A)
        ReadingBackground.DARK -> Color(0xFFE8E4DA)
        else -> if (dark) Color(0xFFF0ECE4) else Color(0xFF272522)
    }
    val faded = when (background) {
        ReadingBackground.SEPIA -> Color(0xFF6B6252)
        ReadingBackground.WHITE -> Color(0xFF5C5C5C)
        ReadingBackground.DARK -> Color(0xFFA8A296)
        else -> if (dark) Color(0xFFB4AEA4) else Color(0xFF68635C)
    }
    val accent = when (background) {
        ReadingBackground.SEPIA -> Color(0xFF8A6D3B)
        ReadingBackground.WHITE -> Color(0xFF3B6EA8)
        ReadingBackground.DARK -> Color(0xFFD8B978)
        else -> if (dark) Color(0xFFD8B978) else Color(0xFF7D6336)
    }
    val highlight = when {
        highlightColor == HighlightColor.YELLOW && background == ReadingBackground.WHITE -> Color(0xFFFFE7A3)
        else -> highlightSwatch(highlightColor, dark)
    }
    return ReadingPalette(
        background = bg,
        ink = ink,
        inkFaded = faded,
        highlightBg = highlight.copy(alpha = 0.72f),
        highlightInk = if (highlightColor == HighlightColor.YELLOW && background == ReadingBackground.DARK) Color(0xFFFFE4A3)
            else if (highlight.luminance() > 0.48f) ink else Color(0xFFFFF8EE),
        divider = when (background) {
            ReadingBackground.SEPIA -> Color(0xFFE6DDC8)
            ReadingBackground.WHITE -> Color(0xFFE0E0E0)
            ReadingBackground.DARK -> Color(0xFF3A3A3A)
            else -> blend(bg, ink, if (dark) 0.18f else 0.12f)
        },
        accent = accent,
        selectionBg = accent.copy(alpha = 0.28f),
        selectionHandle = accent
    )
}

private fun blend(base: Color, overlay: Color, amount: Float) = Color(
    red = base.red * (1f - amount) + overlay.red * amount,
    green = base.green * (1f - amount) + overlay.green * amount,
    blue = base.blue * (1f - amount) + overlay.blue * amount,
    alpha = 1f
)

enum class FontScale(val multiplier: Float) {
    SMALL(0.85f), MEDIUM(1f), LARGE(1.15f), XLARGE(1.3f)
}

object AppearanceState {
    var themeMode by mutableStateOf(ThemeMode.SYSTEM)
    var readingBackground by mutableStateOf(ReadingBackground.SEPIA)
    var fontScale by mutableStateOf(FontScale.MEDIUM)
    var highlightColor by mutableStateOf(HighlightColor.YELLOW)
    var highlightSavedWords by mutableStateOf(true)
}
