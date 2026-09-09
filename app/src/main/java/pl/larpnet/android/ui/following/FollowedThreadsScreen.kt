package pl.larpnet.android.ui.following

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import pl.larpnet.android.R
import pl.larpnet.android.di.rememberAppContainer
import pl.larpnet.android.domain.html.HtmlParser
import pl.larpnet.android.ui.common.AvatarImage
import pl.larpnet.android.ui.common.HtmlContent
import pl.larpnet.android.ui.theme.larpnetTopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowedThreadsScreen(onBack: () -> Unit, onOpenThread: (String) -> Unit) {
    val appContainer = rememberAppContainer()
    val viewModel: FollowedThreadsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { FollowedThreadsViewModel(appContainer.followedThreadsStore, appContainer.statusRepository) }
        },
    )
    val state = viewModel.uiState

    Scaffold(
        topBar = {
            TopAppBar(
                colors = larpnetTopAppBarColors(),
                title = { Text(stringResource(R.string.followed_threads_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading && state.entries.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            state.entries.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.followed_threads_empty))
            }

            else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(state.entries, key = { it.rootStatusId }) { entry ->
                    FollowedThreadRow(
                        entry = entry,
                        onClick = { onOpenThread(entry.rootStatusId) },
                        onUnfollow = { viewModel.unfollow(entry.rootStatusId) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun FollowedThreadRow(entry: FollowedThreadEntry, onClick: () -> Unit, onUnfollow: () -> Unit) {
    val status = entry.rootStatus
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (status != null) {
            AvatarImage(url = status.account.avatar, contentDescription = status.account.displayName)
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = status.account.displayName.ifBlank { status.account.username },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (entry.unreadCount > 0) {
                        Badge(modifier = Modifier.padding(start = 8.dp)) { Text(entry.unreadCount.toString()) }
                    }
                }
                val bodyNodes = remember(status.content) { HtmlParser.parse(status.content) }
                HtmlContent(bodyNodes, modifier = Modifier.padding(top = 2.dp))
            }
        } else {
            Text(
                stringResource(R.string.local_post_list_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        IconButton(onClick = onUnfollow) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.thread_unfollow))
        }
    }
}
