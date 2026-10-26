package com.ziaee.frenchreader.ui.theme

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ziaee.frenchreader.R

val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium)
)

/**
 * Persian letters in Vazirmatn, everything else in the system [systemFamily] ("sans-serif" or "serif"):
 * a Persian translation or meaning gets Vazirmatn even when the app language is English or French.
 * The bundled vazirmatn_persian_* files hold only Arabic-script glyphs, so Latin text falls through
 * to the system font with its real weights. Needs API 29 (custom fallback typefaces).
 */
private class PersianFirstFont(
    override val weight: FontWeight,
    override val style: FontStyle,
    val systemFamily: String
) : AndroidFont(FontLoadingStrategy.Blocking, PersianFirstLoader, FontVariation.Settings())

private object PersianFirstLoader : AndroidFont.TypefaceLoader {
    override fun loadBlocking(context: Context, font: AndroidFont): android.graphics.Typeface? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) persianFirstTypeface(context, font as PersianFirstFont) else null

    override suspend fun awaitLoad(context: Context, font: AndroidFont): android.graphics.Typeface? =
        loadBlocking(context, font)
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun persianFirstTypeface(context: Context, font: PersianFirstFont): android.graphics.Typeface {
    val persian = android.graphics.fonts.Font.Builder(
        context.resources,
        if (font.weight >= FontWeight.Medium) R.font.vazirmatn_persian_medium else R.font.vazirmatn_persian_regular
    ).build()
    val slant = if (font.style == FontStyle.Italic) android.graphics.fonts.FontStyle.FONT_SLANT_ITALIC
        else android.graphics.fonts.FontStyle.FONT_SLANT_UPRIGHT
    return android.graphics.Typeface.CustomFallbackBuilder(android.graphics.fonts.FontFamily.Builder(persian).build())
        .setSystemFallback(font.systemFamily)
        .setStyle(android.graphics.fonts.FontStyle(font.weight.weight, slant))
        .build()
}

private fun persianFirstFamily(systemFamily: String, plain: FontFamily): FontFamily =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) plain
    else FontFamily((100..900 step 100).flatMap { weight ->
        listOf(FontStyle.Normal, FontStyle.Italic).map { PersianFirstFont(FontWeight(weight), it, systemFamily) }
    })

private val PersianFirstSans by lazy { persianFirstFamily("sans-serif", FontFamily.SansSerif) }
private val PersianFirstSerif by lazy { persianFirstFamily("serif", FontFamily.Serif) }

internal fun interfaceFontFamilyFor(language: String): FontFamily =
    if (language == "fa") Vazirmatn else PersianFirstSans

internal fun editorialFontFamilyFor(language: String): FontFamily =
    if (language == "fa") Vazirmatn else PersianFirstSerif

internal fun interfaceTypography(language: String): Typography {
    val fontFamily = interfaceFontFamilyFor(language)
    return Typography().let { base ->
        base.copy(
            displayLarge = base.displayLarge.copy(fontFamily = fontFamily),
            displayMedium = base.displayMedium.copy(fontFamily = fontFamily),
            displaySmall = base.displaySmall.copy(fontFamily = fontFamily),
            headlineLarge = base.headlineLarge.copy(fontFamily = fontFamily),
            headlineMedium = base.headlineMedium.copy(fontFamily = fontFamily),
            headlineSmall = base.headlineSmall.copy(fontFamily = fontFamily),
            titleLarge = base.titleLarge.copy(fontFamily = fontFamily),
            titleMedium = base.titleMedium.copy(fontFamily = fontFamily),
            titleSmall = base.titleSmall.copy(fontFamily = fontFamily),
            bodyLarge = base.bodyLarge.copy(fontFamily = fontFamily),
            bodyMedium = base.bodyMedium.copy(fontFamily = fontFamily),
            bodySmall = base.bodySmall.copy(fontFamily = fontFamily),
            labelLarge = base.labelLarge.copy(fontFamily = fontFamily),
            labelMedium = base.labelMedium.copy(fontFamily = fontFamily),
            labelSmall = base.labelSmall.copy(fontFamily = fontFamily)
        )
    }
}

@Immutable
data class EditorialTypography(
    val screenTitle: TextStyle,
    val sectionTitle: TextStyle,
    val articleHeadline: TextStyle
)

internal fun editorialTypography(language: String): EditorialTypography {
    val fontFamily = editorialFontFamilyFor(language)
    return EditorialTypography(
        screenTitle = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 40.sp,
            lineHeight = 48.sp
        ),
        sectionTitle = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            lineHeight = 34.sp
        ),
        articleHeadline = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 28.sp
        )
    )
}
