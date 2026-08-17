package pl.larpnet.android.ui.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.jsoup.Jsoup
import pl.larpnet.android.R
import pl.larpnet.android.data.model.Notification
import pl.larpnet.android.di.rememberAppContainer
import pl.larpnet.android.ui.common.AvatarImage
import pl.larpnet.android.ui.common.RelativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onOpenStatus: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onSearch: () -> Unit = {},
) {
    val appContainer = rememberAppContainer()
    val viewModel: NotificationsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { NotificationsViewModel(appContainer.notificationRepository) }
        },
    )
    val state = viewModel.uiState
    val listState = rememberLazyListState()

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
                title = { Text(stringResource(R.string.nav_notifications)) },
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search_title))
                    }
                    TextButton(onClick = viewModel::clearAll) {
                        Text(stringResource(R.string.notifications_clear))
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading && state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.notifications_empty))
                }

                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(state.items, key = { it.id }) { notification ->
                        NotificationRow(
                            notification = notification,
                            onClick = {
                                val statusId = notification.status?.id
                                if (statusId != null) onOpenStatus(statusId) else onOpenProfile(notification.account.id)
                            },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(notification: Notification, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        AvatarImage(url = notification.account.avatar, contentDescription = notification.account.displayName)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = "${notification.account.displayName.ifBlank { notification.account.username }} ${notificationTypeLabel(notification.type)}",
                style = MaterialTheme.typography.bodyLarge,
            )
            RelativeTime(notification.createdAt)
            notification.status?.let { status ->
                if (status.content.isNotBlank()) {
                    Text(
                        text = status.spoilerText.ifBlank { Jsoup.parse(status.content).text().take(140) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun notificationTypeLabel(type: String): String = when (type) {
    "favourite" -> stringResource(R.string.notification_favourite)
    "reblog" -> stringResource(R.string.notification_reblog)
    "mention" -> stringResource(R.string.notification_mention)
    "follow" -> stringResource(R.string.notification_follow)
    "follow_request" -> stringResource(R.string.notification_follow_request)
    "poll" -> stringResource(R.string.notification_poll)
    "quote", "quoted_update" -> stringResource(R.string.notification_quote)
    else -> stringResource(R.string.notification_generic)
}
