package pl.larpnet.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * A single fixed light palette, deliberately not following the system dark-mode setting --
 * matches the iOS app (`UI/Theme/Theme.swift`) and the production web theme, neither of which
 * has a dark variant. Consistency with the actual site takes priority over Android's usual
 * light/dark convention here.
 */
private val LightColors = lightColorScheme(
    primary = LarpnetAccent,
    onPrimary = Color.White,
    secondary = LarpnetAccent,
    onSecondary = Color.White,
    background = LarpnetPageBackground,
    onBackground = LarpnetBodyText,
    surface = LarpnetCardBackground,
    onSurface = LarpnetBodyText,
    surfaceVariant = LarpnetHighlight,
    onSurfaceVariant = LarpnetMutedText,
    error = LarpnetError,
)

@Composable
fun LarpnetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = LarpnetTypography,
        content = content,
    )
}
