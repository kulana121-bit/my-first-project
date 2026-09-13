package com.notes.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = SoftBlue,
    secondary = AccentMint,
    background = SoftBackground,
    surface = SoftCard
)

private val DarkColors = darkColorScheme(
    primary = SoftBlue,
    secondary = AccentMint,
    background = DarkBackground,
    surface = DarkCard
)

@Composable
fun NotesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
