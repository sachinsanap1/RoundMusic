package com.roundmusic.player.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val PurpleColorScheme = darkColorScheme(
    primary = PurpleAccent,
    onPrimary = TextPrimary,
    primaryContainer = PurpleAccentDark,
    onPrimaryContainer = TextPrimary,
    secondary = PurpleAccentLight,
    onSecondary = PurpleBackground,
    secondaryContainer = PurpleAccentDark,
    onSecondaryContainer = TextPrimary,
    background = PurpleBackground,
    onBackground = TextPrimary,
    surface = PurpleSurface,
    onSurface = TextPrimary,
    surfaceVariant = PurpleCard,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = PurpleCard,
    surfaceContainerHigh = PurpleCardHigh,
    surfaceContainerHighest = PurpleCardHigh,
    outline = TextSecondary,
    error = ErrorRed
)

private val RoundShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/** Phase 1 has the default purple theme. The AMOLED true-black option is added in Phase 7. */
@Composable
fun RoundMusicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PurpleColorScheme,
        shapes = RoundShapes,
        content = content
    )
}
