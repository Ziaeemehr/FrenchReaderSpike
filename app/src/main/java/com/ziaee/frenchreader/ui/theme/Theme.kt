package com.ziaee.frenchreader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration

/** Which color scheme the whole app uses -- see ROADMAP.md section 7. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Pure decision logic extracted out of [FrenchReaderTheme] so it's unit
 * testable without needing a Compose/Android runtime for
 * [isSystemInDarkTheme]. */
internal fun resolveDarkTheme(mode: ThemeMode, systemInDarkTheme: Boolean): Boolean = when (mode) {
    ThemeMode.SYSTEM -> systemInDarkTheme
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun FrenchReaderTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val darkTheme = resolveDarkTheme(themeMode, isSystemInDarkTheme())
    val language = LocalConfiguration.current.locales[0].language
    CompositionLocalProvider(
        LocalAppSpacing provides AppSpacing(),
        LocalAppSizes provides AppSizes(),
        LocalAppElevations provides AppElevations(),
        LocalEditorialTypography provides editorialTypography(language)
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) FrenchReaderDarkColorScheme else FrenchReaderLightColorScheme,
            typography = interfaceTypography(language),
            shapes = FrenchReaderShapes,
            content = content
        )
    }
}

/** Which fixed color set the reading screen uses -- independent of
 * [ThemeMode], like a book-reader app's own reading theme. See
 * ROADMAP.md section 7. */
enum class ReadingBackground { SEPIA, WHITE, DARK }

/** A reading screen's full color set. [ReadingBackground.SEPIA]'s values
 * are byte-identical to the original hardcoded ReadingPalette in
 * ReadingScreen.kt, so someone who never opens Settings sees no change. */
data class ReadingPalette(
    val background: Color,
    val ink: Color,
    val inkFaded: Color,
    val highlightBg: Color,
    val highlightInk: Color,
    val divider: Color,
    val accent: Color
)

fun readingPaletteFor(background: ReadingBackground): ReadingPalette = when (background) {
    ReadingBackground.SEPIA -> ReadingPalette(
        background = Color(0xFFFBF6EC),
        ink = Color(0xFF2E2A22),
        inkFaded = Color(0xFF6B6252),
        highlightBg = Color(0xFFF6D97A),
        highlightInk = Color(0xFF2E2A22),
        divider = Color(0xFFE6DDC8),
        accent = Color(0xFF8A6D3B)
    )
    ReadingBackground.WHITE -> ReadingPalette(
        background = Color(0xFFFFFFFF),
        ink = Color(0xFF1A1A1A),
        inkFaded = Color(0xFF5C5C5C),
        highlightBg = Color(0xFFFFE082),
        highlightInk = Color(0xFF1A1A1A),
        divider = Color(0xFFE0E0E0),
        accent = Color(0xFF3B6EA8)
    )
    ReadingBackground.DARK -> ReadingPalette(
        background = Color(0xFF1A1A1A),
        ink = Color(0xFFE8E4DA),
        inkFaded = Color(0xFFA8A296),
        highlightBg = Color(0xFF4A3F1E),
        highlightInk = Color(0xFFF6D97A),
        divider = Color(0xFF3A3A3A),
        accent = Color(0xFFD8B978)
    )
}

/** How large the reading screen's text renders -- a multiplier applied to
 * every hardcoded sp value in ReadingScreen.kt. See ROADMAP.md section 7. */
enum class FontScale(val multiplier: Float) {
    SMALL(0.85f),
    MEDIUM(1f),
    LARGE(1.15f),
    XLARGE(1.3f)
}

/** Live, in-memory mirror of the three appearance settings -- read
 * directly by composables (no CompositionLocal, no ViewModel/DI; matches
 * this codebase's existing lightweight style). [com.ziaee.frenchreader.MainActivity]
 * initializes these three fields from [com.ziaee.frenchreader.data.AppearancePrefs]
 * once at startup; SettingsScreen updates both the field here (for
 * immediate recomposition) and the persisted pref (Task 2/3) on every
 * change. */
object AppearanceState {
    var themeMode by mutableStateOf(ThemeMode.SYSTEM)
    var readingBackground by mutableStateOf(ReadingBackground.SEPIA)
    var fontScale by mutableStateOf(FontScale.MEDIUM)
}
