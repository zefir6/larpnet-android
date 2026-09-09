package pl.larpnet.android.ui.moderation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import pl.larpnet.android.R
import pl.larpnet.android.di.rememberAppContainer
import pl.larpnet.android.ui.theme.larpnetTopAppBarColors
import pl.larpnet.android.ui.timeline.StatusCard

/** Which local filter list this screen shows -- see [LocalPostFilterStore]. */
enum class LocalPostListKind { HIDDEN, BLOCKED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalPostListScreen(
    kind: LocalPostListKind,
    onBack: () -> Unit,
    onOpenThread: (String) -> Unit,
) {
    val appContainer = rememberAppContainer()
    val store = if (kind == LocalPostListKind.HIDDEN) appContainer.hiddenPostsStore else appContainer.blockedPostsStore
    val viewModel: LocalPostListViewModel = viewModel(
        key = "local_post_list_$kind",
        factory = viewModelFactory { initializer { LocalPostListViewModel(store, appContainer.statusRepository) } },
    )
    val state = viewModel.uiState
    val title = stringResource(if (kind == LocalPostListKind.HIDDEN) R.string.hidden_posts_title else R.string.blocked_posts_title)
    val emptyText = stringResource(if (kind == LocalPostListKind.HIDDEN) R.string.hidden_posts_empty else R.string.blocked_posts_empty)
    val unfilterLabel = stringResource(
        if (kind == LocalPostListKind.HIDDEN) R.string.local_post_list_unhide else R.string.local_post_list_unblock,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                colors = larpnetTopAppBarColors(),
                title = { Text(title) },
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
                Text(emptyText)
            }

            else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(state.entries, key = { it.id }) { entry ->
                    if (entry.status != null) {
                        StatusCard(
                            status = entry.status,
                            onOpenThread = { onOpenThread(it.id) },
                            onOpenProfile = {},
                            onReply = {},
                            onToggleFavourite = {},
                            onToggleReblog = {},
                            onToggleBookmark = {},
                            flat = true,
                        )
                    } else {
                        Text(
                            stringResource(R.string.local_post_list_unavailable),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        OutlinedButton(onClick = { viewModel.remove(entry.id) }) { Text(unfilterLabel) }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
