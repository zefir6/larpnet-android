package pl.larpnet.android.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.jsoup.Jsoup
import pl.larpnet.android.R
import pl.larpnet.android.data.model.Conversation
import pl.larpnet.android.di.rememberAppContainer
import pl.larpnet.android.ui.common.AvatarImage
import pl.larpnet.android.ui.common.RelativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    onBack: () -> Unit,
    onOpenConversation: (Conversation) -> Unit,
    onNewMessage: () -> Unit,
) {
    val appContainer = rememberAppContainer()
    val viewModel: ConversationsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ConversationsViewModel(appContainer.conversationRepository) }
        },
    )
    val state = viewModel.uiState
    val listState = rememberLazyListState()

    // The MESSAGES back-stack entry (and its ViewModel) survives navigating to the thread
    // screen and back, so a refresh on re-entry is needed to pick up e.g. a just-sent
    // message's conversation without waiting for pull-to-refresh.
    LaunchedEffect(Unit) { viewModel.refresh() }

    LaunchedEffect(listState, state.items.size, state.canLoadMore) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                if (last != null && state.items.isNotEmpty() && last >= state.items.size - 3) {
                    viewModel.loadMore()
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.messages_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewMessage) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.messages_new))
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                state.isLoading && state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.error != null && state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.error, color = MaterialTheme.colorScheme.error)
                }

                state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.messages_empty))
                }

                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(state.items, key = { it.id }) { conversation ->
                        ConversationRow(
                            conversation = conversation,
                            onClick = { onOpenConversation(conversation) },
                            onDelete = { viewModel.delete(conversation) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(conversation: Conversation, onClick: () -> Unit, onDelete: () -> Unit) {
    val account = conversation.accounts.firstOrNull()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarImage(url = account?.avatar, contentDescription = account?.displayName)
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                text = account?.displayName?.ifBlank { account.username } ?: "",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (conversation.unread) FontWeight.Bold else FontWeight.Normal,
            )
            conversation.lastStatus?.let { status ->
                Text(
                    text = Jsoup.parse(status.content).text().take(140),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (conversation.unread) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 2.dp),
                )
                RelativeTime(status.createdAt, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (conversation.unread) {
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(10.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
        }
    }
}
