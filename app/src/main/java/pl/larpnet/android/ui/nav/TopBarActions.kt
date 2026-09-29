package pl.larpnet.android.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import pl.larpnet.android.R

/**
 * The top-bar zone's entry point -- a `TopAppBar.navigationIcon` that opens a dropdown listing
 * whatever destinations the user has assigned to [NavigationLayoutStore.topBar]. Renders nothing
 * when that zone is empty, same as iOS's `Menu` being conditionally omitted. iOS equivalent:
 * `RootView`'s shared leading `Menu` toolbar item.
 */
@Composable
fun TopBarMenuButton(topBar: List<AppDestination>, onSelect: (AppDestination) -> Unit) {
    if (topBar.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.nav_menu))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        topBar.forEach { destination ->
            DropdownMenuItem(
                text = { Text(stringResource(destination.labelRes)) },
                leadingIcon = { Icon(destination.icon, contentDescription = null) },
                onClick = {
                    expanded = false
                    onSelect(destination)
                },
            )
        }
    }
}

/**
 * The fixed, non-customizable notifications shortcut -- a `TopAppBar.actions` icon shown on
 * every top-level screen except Notifications itself (no point linking to the screen you're
 * already on). iOS equivalent: `RootView`'s shared trailing bell toolbar item.
 */
@Composable
fun NotificationsBellAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Filled.Notifications, contentDescription = stringResource(R.string.nav_notifications))
    }
}
