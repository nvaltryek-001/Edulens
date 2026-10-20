package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,

    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,

    tertiary = md_theme_dark_secondary,
    onTertiary = md_theme_dark_onSecondary,
    tertiaryContainer = md_theme_dark_secondaryContainer,
    onTertiaryContainer = md_theme_dark_onSecondaryContainer,

    error = md_theme_dark_error,
    errorContainer = Color(0xFF7F1D1D),
    onError = md_theme_dark_onError,
    onErrorContainer = Color(0xFFFFDAD6),

    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,

    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,

    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,

    outline = md_theme_dark_outline,

    inverseOnSurface = md_theme_light_onSurface,
    inverseSurface = md_theme_light_surface,
    inversePrimary = md_theme_light_primary
)

private val LightColorScheme = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,

    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,

    tertiary = md_theme_light_secondary,
    onTertiary = md_theme_light_onSecondary,
    tertiaryContainer = md_theme_light_secondaryContainer,
    onTertiaryContainer = md_theme_light_onSecondaryContainer,

    error = md_theme_light_error,
    errorContainer = Color(0xFFFFDAD6),
    onError = md_theme_light_onError,
    onErrorContainer = Color(0xFF410002),

    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,

    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,

    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,

    outline = md_theme_light_outline,

    inverseOnSurface = md_theme_dark_onSurface,
    inverseSurface = md_theme_dark_surface,
    inversePrimary = md_theme_dark_primary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}