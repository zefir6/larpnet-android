package pl.larpnet.android.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.absoluteValue

/**
 * A single-letter circular avatar for a chat room/contact -- used wherever there is no real
 * Matrix room avatar to load (this app doesn't resolve mxc:// room avatars into an authenticated
 * Coil request yet, see [pl.larpnet.android.data.matrix.ChatRoom] -- unlike [AvatarImage], which
 * loads a real Mastodon account avatar URL). The background color is a deterministic hash of
 * [name] rather than one fixed color, so a room list full of these still reads as visually
 * distinct rows at a glance, same as Element/most other chat apps do for text-only avatars.
 */
@Composable
fun InitialsAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val letter = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val background = avatarColorFor(name)
    val onBackground = if (background.luminance() > 0.5f) Color.Black else Color.White
    Box(
        modifier = modifier
            .size(size)
            .background(background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            color = onBackground,
            style = MaterialTheme.typography.titleMedium,
            fontSize = (size.value / 2.2f).sp,
        )
    }
}

/** A fixed palette (not an arbitrary HSV hash) so every color stays legible with white/black text
 * and visually fits the app's own purple-accented theme rather than clashing with it. */
private val avatarPalette = listOf(
    Color(0xFF833C89), // Larpnet nav-bar purple
    Color(0xFFA54BAD), // Larpnet accent purple
    Color(0xFF3C7A89), // teal
    Color(0xFF89623C), // brown
    Color(0xFF4B8A3C), // green
    Color(0xFF8A3C4B), // maroon
    Color(0xFF3C5A8A), // blue
    Color(0xFF8A7A3C), // olive
)

private fun avatarColorFor(name: String): Color =
    avatarPalette[name.hashCode().absoluteValue % avatarPalette.size]
