package pl.larpnet.android.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import pl.larpnet.android.R

/**
 * [visibility] is the raw server string ("public"|"unlisted"|"private") -- "direct" is
 * included for compose-time selection even though the server never round-trips it back
 * (see data/model/Status.kt doc comment).
 */
@Composable
fun VisibilityIcon(visibility: String, modifier: Modifier = Modifier) {
    val (icon, description) = when (visibility) {
        "public" -> Icons.Filled.Public to stringResource(R.string.compose_visibility_public)
        "unlisted" -> Icons.Filled.LockOpen to stringResource(R.string.compose_visibility_unlisted)
        "private" -> Icons.Filled.Lock to stringResource(R.string.compose_visibility_private)
        "direct" -> Icons.Filled.AlternateEmail to stringResource(R.string.compose_visibility_direct)
        "custom" -> Icons.Filled.Groups to stringResource(R.string.compose_visibility_custom)
        else -> Icons.Filled.Public to visibility
    }
    Icon(
        imageVector = icon,
        contentDescription = description,
        modifier = modifier,
    )
}

val visibilityOptions = listOf("public", "unlisted", "private", "direct", "custom")

@Composable
fun visibilityLabel(visibility: String): String = when (visibility) {
    "public" -> stringResource(R.string.compose_visibility_public)
    "unlisted" -> stringResource(R.string.compose_visibility_unlisted)
    "private" -> stringResource(R.string.compose_visibility_private)
    "direct" -> stringResource(R.string.compose_visibility_direct)
    "custom" -> stringResource(R.string.compose_visibility_custom)
    else -> visibility
}
