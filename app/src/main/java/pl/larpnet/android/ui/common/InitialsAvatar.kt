package pl.larpnet.android.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.larpnet.android.ui.theme.LarpnetAccent
import pl.larpnet.android.ui.theme.LarpnetNavBar

/**
 * A single-letter circular avatar for a chat room/contact -- used wherever there is no real
 * Matrix room/sender avatar to load. The background color is a deterministic hash of [name]
 * rather than one fixed color, so a room list or a group chat full of these still reads as
 * visually distinct at a glance, same as Element/most other chat apps do for text-only avatars
 * -- the counterpart of larpnet-iOS's `UI/Common/InitialsAvatar.swift`, same fixed palette (not
 * an arbitrary HSV hash) for cross-platform consistency and to keep every color legible with
 * white text against the app's own purple-accented theme.
 */
@Composable
fun InitialsAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val palette = remember { listOf(
        LarpnetNavBar,
        LarpnetAccent,
        Color(0xFF3C7A89), // teal
        Color(0xFF89623C), // brown
        Color(0xFF4B8A3C), // green
        Color(0xFF8A3C4B), // maroon
        Color(0xFF3C5A8A), // blue
        Color(0xFF8A7A3C), // olive
    ) }
    val letter = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val background = palette[Math.floorMod(name.hashCode(), palette.size)]

    Box(
        modifier = modifier.size(size).background(background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = letter, color = Color.White, fontWeight = FontWeight.Medium, fontSize = (size.value / 2.2).sp)
    }
}
