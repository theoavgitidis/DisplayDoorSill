package com.example.desl_app.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val LightScheme = lightColorScheme(
    primary = DisplayBlue,
    onPrimary = AppWhite,

    background = AppWhite,
    onBackground = TextPrimary,

    surface = AppWhite,
    onSurface = TextPrimary,

    outline = GreyBorder
)

@Composable
fun DisplayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightScheme,
        typography = DisplayTypography
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background
        ) {
            // <- DAS erzwingt global "Text ist schwarz"
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onBackground
            ) {
                content()
            }
        }
    }
}
