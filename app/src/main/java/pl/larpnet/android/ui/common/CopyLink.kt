package pl.larpnet.android.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import pl.larpnet.android.R

/**
 * Puts a profile link on the clipboard. Android 13+ shows its own "copied" confirmation for
 * every clipboard write, so the toast is only for older versions (where nothing would show).
 * Mirrors the web's "Kopiuj link" buttons (profile card, chat info panel, member rows).
 */
fun copyLinkToClipboard(context: Context, url: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.copy_profile_link), url))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, R.string.link_copied, Toast.LENGTH_SHORT).show()
    }
}

/** Icon-only "copy profile link" button. */
@Composable
fun CopyProfileLinkButton(url: String, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val context = LocalContext.current
    IconButton(onClick = { copyLinkToClipboard(context, url) }, modifier = modifier) {
        Icon(Icons.Filled.Link, contentDescription = contentDescription ?: stringResource(R.string.copy_profile_link))
    }
}
