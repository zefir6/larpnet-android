package pl.larpnet.android.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Deep-purple bar with white title/icons -- the single most recognizable piece of the site's
 * brand identity. Android analogue of iOS's `larpnetNavigationBarStyle()`; apply via each
 * `TopAppBar`'s `colors` parameter. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun larpnetTopAppBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = LarpnetNavBar,
    scrolledContainerColor = LarpnetNavBar,
    titleContentColor = Color.White,
    navigationIconContentColor = Color.White,
    actionIconContentColor = Color.White,
)

/** White, rounded, subtly-shadowed card -- matches the web theme's `.panel` component (posts,
 * profile headers, etc. are all `.panel`s there) and iOS's `.larpnetCard()`. */
fun Modifier.larpnetCard(): Modifier {
    val shape = RoundedCornerShape(4.dp)
    return this
        .shadow(elevation = 2.dp, shape = shape, clip = false)
        .background(LarpnetCardBackground, shape)
}
