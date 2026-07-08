package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = PolishPrimaryDark,
    secondary = PolishSecondaryDark,
    tertiary = PolishTertiaryDark,
    background = PolishBgDark,
    surface = PolishSurfaceDark,
    onPrimary = PolishBgDark,
    onSecondary = PolishBgDark,
    onTertiary = PolishBgDark,
    onBackground = PolishTextLight,
    onSurface = PolishTextLight,
    primaryContainer = PolishPrimaryContainerDark,
    onPrimaryContainer = PolishOnPrimaryContainerDark
)

private val LightColorScheme = lightColorScheme(
    primary = PolishPrimaryLight,
    secondary = PolishSecondaryLight,
    tertiary = PolishTertiaryLight,
    background = PolishBgLight,
    surface = PolishSurfaceLight,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = PolishTextDark,
    onSurface = PolishTextDark,
    primaryContainer = PolishPrimaryContainerLight,
    onPrimaryContainer = PolishOnPrimaryContainerLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
