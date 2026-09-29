package pl.larpnet.android.ui.nav

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import pl.larpnet.android.R
import pl.larpnet.android.ui.theme.larpnetTopAppBarColors

/**
 * The bottom-right "More" tab's root -- everything not currently in the bottom bar or the
 * top-bar zone, reached the same way the top-bar dropdown reaches its items ([onSelect]).
 * iOS equivalent: `RootView.moreContent`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    more: List<AppDestination>,
    topBar: List<AppDestination>,
    onSelect: (AppDestination) -> Unit,
    onOpenNotifications: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                colors = larpnetTopAppBarColors(),
                title = { Text(stringResource(R.string.nav_more)) },
                navigationIcon = { TopBarMenuButton(topBar, onSelect) },
                actions = { NotificationsBellAction(onOpenNotifications) },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(more, key = { it.name }) { destination ->
                ListItem(
                    headlineContent = { Text(stringResource(destination.labelRes)) },
                    leadingContent = { Icon(destination.icon, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(destination) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
