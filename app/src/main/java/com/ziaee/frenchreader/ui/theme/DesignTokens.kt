package com.ziaee.frenchreader.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class AppSpacing(
    val half: Dp = 4.dp,
    val xSmall: Dp = 8.dp,
    val small: Dp = 16.dp,
    val medium: Dp = 24.dp,
    val large: Dp = 32.dp,
    val xLarge: Dp = 48.dp
)

@Immutable
data class AppSizes(
    val iconSmall: Dp = 18.dp,
    val icon: Dp = 24.dp,
    val touchTarget: Dp = 48.dp,
    val thumbnailSmall: Dp = 48.dp,
    val thumbnailMedium: Dp = 72.dp,
    val readerMeasure: Dp = 640.dp,
    val playerPrimaryControl: Dp = 48.dp
)

@Immutable
data class AppElevations(
    val flat: Dp = 0.dp,
    val card: Dp = 1.dp,
    val raised: Dp = 3.dp,
    val overlay: Dp = 6.dp
)

val FrenchReaderShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

internal val LocalAppSpacing = staticCompositionLocalOf { AppSpacing() }
internal val LocalAppSizes = staticCompositionLocalOf { AppSizes() }
internal val LocalAppElevations = staticCompositionLocalOf { AppElevations() }
internal val LocalEditorialTypography = staticCompositionLocalOf {
    editorialTypography("fr")
}

object FrenchReaderDesign {
    val spacing: AppSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalAppSpacing.current

    val sizes: AppSizes
        @Composable
        @ReadOnlyComposable
        get() = LocalAppSizes.current

    val elevations: AppElevations
        @Composable
        @ReadOnlyComposable
        get() = LocalAppElevations.current

    val editorialTypography: EditorialTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalEditorialTypography.current
}
