package pl.larpnet.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import pl.larpnet.android.R

/** Bundled from the same files as the iOS app's `Resources/Fonts/` -- see Color.kt's doc comment. */
val OpenSansFamily = FontFamily(
    Font(R.font.open_sans_regular, FontWeight.Normal),
    Font(R.font.open_sans_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.open_sans_semibold, FontWeight.SemiBold),
    Font(R.font.open_sans_bold, FontWeight.Bold),
)

private val defaultTypography = Typography()

/** Material3's default type scale, with every slot switched to [OpenSansFamily] -- mirrors the
 * iOS app applying one custom font app-wide via `.environment(\.font, LarpnetTheme.bodyFont)`. */
val LarpnetTypography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(fontFamily = OpenSansFamily),
    displayMedium = defaultTypography.displayMedium.copy(fontFamily = OpenSansFamily),
    displaySmall = defaultTypography.displaySmall.copy(fontFamily = OpenSansFamily),
    headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = OpenSansFamily),
    headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = OpenSansFamily),
    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = OpenSansFamily),
    titleLarge = defaultTypography.titleLarge.copy(fontFamily = OpenSansFamily, fontWeight = FontWeight.SemiBold),
    titleMedium = defaultTypography.titleMedium.copy(fontFamily = OpenSansFamily, fontWeight = FontWeight.SemiBold),
    titleSmall = defaultTypography.titleSmall.copy(fontFamily = OpenSansFamily, fontWeight = FontWeight.SemiBold),
    bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = OpenSansFamily),
    bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = OpenSansFamily),
    bodySmall = defaultTypography.bodySmall.copy(fontFamily = OpenSansFamily),
    labelLarge = defaultTypography.labelLarge.copy(fontFamily = OpenSansFamily, fontWeight = FontWeight.SemiBold),
    labelMedium = defaultTypography.labelMedium.copy(fontFamily = OpenSansFamily, fontWeight = FontWeight.Medium),
    labelSmall = defaultTypography.labelSmall.copy(fontFamily = OpenSansFamily, fontWeight = FontWeight.Medium),
)
