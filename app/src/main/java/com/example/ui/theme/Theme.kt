package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val MemoMindLightColorScheme = lightColorScheme(
    primary = ObsidianInk,
    onPrimary = PureWhite,
    primaryContainer = MutedSurface,
    onPrimaryContainer = CharcoalText,
    secondary = CharcoalText,
    onSecondary = PureWhite,
    secondaryContainer = MutedSurface,
    onSecondaryContainer = CharcoalText,
    tertiary = RecordingCrimson,
    onTertiary = PureWhite,
    tertiaryContainer = RecordingCrimsonSoft,
    onTertiaryContainer = RecordingCrimsonDark,
    background = SoftStoneCanvas,
    onBackground = CharcoalText,
    surface = ElevatedWhiteCard,
    onSurface = CharcoalText,
    surfaceVariant = MutedSurface,
    onSurfaceVariant = SlateSecondaryText,
    outline = HairlineBorder,
    outlineVariant = StrongBorder,
    error = RecordingCrimson,
    onError = PureWhite,
    errorContainer = RecordingCrimsonSoft,
    onErrorContainer = RecordingCrimsonDark
)

private val MemoMindDarkColorScheme = darkColorScheme(
    primary = PureWhite,
    onPrimary = ObsidianInk,
    primaryContainer = DarkSurfaceElevated,
    onPrimaryContainer = DarkOnSurface,
    secondary = DarkOnSurface,
    onSecondary = ObsidianInk,
    secondaryContainer = DarkSurfaceElevated,
    onSecondaryContainer = DarkOnSurface,
    tertiary = RecordingCrimson,
    onTertiary = PureWhite,
    tertiaryContainer = RecordingCrimsonDark,
    onTertiaryContainer = RecordingCrimsonSoft,
    background = DarkCanvas,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = DarkSecondaryText,
    outline = DarkBorder,
    outlineVariant = DarkBorder,
    error = RecordingCrimson,
    onError = PureWhite,
    errorContainer = RecordingCrimsonDark,
    onErrorContainer = RecordingCrimsonSoft
)

val MemoMindShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MemoMindTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) MemoMindDarkColorScheme else MemoMindLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MemoMindTypography,
        shapes = MemoMindShapes,
        content = content
    )
}
