package pl.larpnet.android.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import pl.larpnet.android.R
import pl.larpnet.android.data.matrix.ChatRoom
import pl.larpnet.android.data.matrix.MatrixRepository
import pl.larpnet.android.di.rememberAppContainer
import pl.larpnet.android.ui.nav.AppDestination
import pl.larpnet.android.ui.nav.NotificationsBellAction
import pl.larpnet.android.ui.nav.TopBarMenuButton
import pl.larpnet.android.ui.common.MatrixAvatarImage
import pl.larpnet.android.ui.common.RelativeTime
import pl.larpnet.android.ui.theme.LarpnetAccent
import pl.larpnet.android.ui.theme.larpnetTopAppBarColors
import kotlinx.datetime.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpenRoom: (ChatRoom) -> Unit,
    onNewChat: () -> Unit,
    topBar: List<AppDestination> = emptyList(),
    onOpenTopBarDestination: (AppDestination) -> Unit = {},
    onOpenNotifications: () -> Unit = {},
) {
    val appContainer = rememberAppContainer()
    val viewModel: ChatViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ChatViewModel(appContainer.matrixRepository) }
        },
    )
    val state = viewModel.uiState

    state.recoveryPrompt?.let { kind ->
        RecoveryKeyDialog(
            mode = if (kind == MatrixRepository.RecoveryPromptKind.NEEDS_SETUP) RecoveryKeyMode.SETUP else RecoveryKeyMode.RESTORE,
            repository = appContainer.matrixRepository,
            onDone = viewModel::dismissRecoveryPrompt,
            onSkip = if (kind == MatrixRepository.RecoveryPromptKind.NEEDS_RESTORE) viewModel::dismissRecoveryPrompt else null,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = larpnetTopAppBarColors(),
                title = { Text(stringResource(R.string.chat_title)) },
                navigationIcon = { TopBarMenuButton(topBar, onOpenTopBarDestination) },
                actions = { NotificationsBellAction(onOpenNotifications) },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewChat) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.chat_new))
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isLoading && state.rooms.isNotEmpty(),
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                state.isLoading && state.rooms.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.error != null && state.rooms.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.error, color = MaterialTheme.colorScheme.error)
                }

                state.rooms.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.chat_empty))
                }

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.rooms, key = { it.id }) { room ->
                        ChatRoomRow(
                            room = room,
                            repository = appContainer.matrixRepository,
                            showTimestamp = appContainer.tokenStore.showChatTimestamps,
                            onClick = { onOpenRoom(room) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatRoomRow(room: ChatRoom, repository: MatrixRepository, showTimestamp: Boolean, onClick: () -> Unit) {
    val hasUnread = room.unreadCount > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MatrixAvatarImage(avatarUrl = room.avatarUrl, name = room.name, repository = repository)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = room.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (hasUnread) FontWeight.SemiBold else FontWeight.Medium,
            )
            room.preview?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            if (showTimestamp && room.timestampMillis != null) {
                RelativeTime(
                    instant = Instant.fromEpochMilliseconds(room.timestampMillis),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (hasUnread) {
                Text(
                    text = if (room.unreadCount > 99) "99+" else room.unreadCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .background(LarpnetAccent, CircleShape)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
        }
    }
}
