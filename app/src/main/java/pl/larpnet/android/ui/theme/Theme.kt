package pl.larpnet.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = LarpnetVioletDark,
    onPrimary = LarpnetBackgroundDark,
    background = LarpnetBackgroundDark,
    surface = LarpnetSurfaceDark,
    error = LarpnetError,
)

private val LightColors = lightColorScheme(
    primary = LarpnetVioletLight,
    onPrimary = LarpnetBackgroundLight,
    background = LarpnetBackgroundLight,
    surface = LarpnetSurfaceLight,
    error = LarpnetError,
)

@Composable
fun LarpnetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = LarpnetTypography,
        content = content,
    )
}
