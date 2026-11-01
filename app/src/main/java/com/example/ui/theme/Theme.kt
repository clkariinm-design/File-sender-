package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DeepSpaceColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = DeepSpaceBlack,
    primaryContainer = CyanGlow,
    onPrimaryContainer = ElectricCyan,
    secondary = AuroraViolet,
    onSecondary = Color.White,
    secondaryContainer = VioletGlow,
    onSecondaryContainer = AuroraViolet,
    tertiary = NeonEmerald,
    onTertiary = DeepSpaceBlack,
    tertiaryContainer = EmeraldGlow,
    onTertiaryContainer = NeonEmerald,
    background = DeepSpaceBlack,
    onBackground = TextPrimary,
    surface = DeepSpaceSurface,
    onSurface = TextPrimary,
    surfaceVariant = DeepSpaceCard,
    onSurfaceVariant = TextSecondary,
    outline = GlassBorder,
    outlineVariant = GlassBorderHighlight,
    error = RoseError,
    onError = Color.White
)

@Composable
fun XShareTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DeepSpaceColorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    XShareTheme(content = content)
}
