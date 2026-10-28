package com.divineit.convertor.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    secondary = Gold,
    background = Paper,
    surface = Color.White,
    onBackground = Ink,
    onSurface = Ink,
    error = Bad
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7FB3D5),
    onPrimary = Color.Black,
    secondary = Gold,
    background = SurfaceDark,
    surface = CardDark,
    onBackground = Color.White,
    onSurface = Color.White,
    error = Color(0xFFE57373)
)

@Composable
fun ConvertorTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = AppTypography,
        content = content
    )
}
