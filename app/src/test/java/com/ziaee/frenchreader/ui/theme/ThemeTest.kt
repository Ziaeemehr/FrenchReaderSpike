package com.ziaee.frenchreader.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTest {
    @Test
    fun `light scheme uses editorial palette anchors`() {
        assertEquals(Color(0xFFF7F1E7), FrenchReaderLightColorScheme.background)
        assertEquals(Color(0xFF7A2432), FrenchReaderLightColorScheme.primary)
        assertEquals(Color(0xFF2F6F6A), FrenchReaderLightColorScheme.secondary)
        assertEquals(Color(0xFFA66A16), FrenchReaderLightColorScheme.tertiary)
    }

    @Test
    fun `dark scheme is warm and not pure black`() {
        assertEquals(Color(0xFF181513), FrenchReaderDarkColorScheme.background)
        assertEquals(Color(0xFFE0A2AA), FrenchReaderDarkColorScheme.primary)
    }

    @Test
    fun `Persian typography keeps Vazirmatn while French editorial text uses serif`() {
        assertEquals(Vazirmatn, interfaceFontFamilyFor("fa"))
        assertEquals(Vazirmatn, editorialFontFamilyFor("fa"))
        assertEquals(FontFamily.SansSerif, interfaceFontFamilyFor("fr"))
        assertEquals(FontFamily.Serif, editorialFontFamilyFor("fr"))
    }

    @Test
    fun `SEPIA palette matches the original hardcoded ReadingPalette values`() {
        val palette = readingPaletteFor(ReadingBackground.SEPIA)
        assertEquals(Color(0xFFFBF6EC), palette.background)
        assertEquals(Color(0xFF2E2A22), palette.ink)
        assertEquals(Color(0xFF6B6252), palette.inkFaded)
        assertEquals(Color(0xFFF6D97A), palette.highlightBg)
        assertEquals(Color(0xFF2E2A22), palette.highlightInk)
        assertEquals(Color(0xFFE6DDC8), palette.divider)
        assertEquals(Color(0xFF8A6D3B), palette.accent)
    }

    @Test
    fun `WHITE and DARK palettes are distinct from SEPIA and from each other`() {
        val sepia = readingPaletteFor(ReadingBackground.SEPIA)
        val white = readingPaletteFor(ReadingBackground.WHITE)
        val dark = readingPaletteFor(ReadingBackground.DARK)
        assertEquals(Color(0xFFFFFFFF), white.background)
        assertEquals(Color(0xFF1A1A1A), dark.background)
        assert(sepia.background != white.background)
        assert(white.background != dark.background)
    }

    @Test
    fun `resolveDarkTheme follows the system when mode is SYSTEM`() {
        assertEquals(true, resolveDarkTheme(ThemeMode.SYSTEM, systemInDarkTheme = true))
        assertEquals(false, resolveDarkTheme(ThemeMode.SYSTEM, systemInDarkTheme = false))
    }

    @Test
    fun `resolveDarkTheme ignores the system when mode is an explicit override`() {
        assertEquals(false, resolveDarkTheme(ThemeMode.LIGHT, systemInDarkTheme = true))
        assertEquals(true, resolveDarkTheme(ThemeMode.DARK, systemInDarkTheme = false))
    }

    @Test
    fun `FontScale multipliers are in ascending order from SMALL to XLARGE`() {
        assertEquals(0.85f, FontScale.SMALL.multiplier)
        assertEquals(1f, FontScale.MEDIUM.multiplier)
        assertEquals(1.15f, FontScale.LARGE.multiplier)
        assertEquals(1.3f, FontScale.XLARGE.multiplier)
    }
}
