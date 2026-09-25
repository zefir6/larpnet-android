package pl.larpnet.android.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import pl.larpnet.android.ui.theme.larpnetTopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: (() -> Unit)?,
    onOpenRoom: (ChatRoom) -> Unit,
    onNewChat: () -> Unit,
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
                navigationIcon = {
                    // Only shown when actually pushed on top of something (e.g. from
                    // Notifications' toolbar icon) -- null when this is the bottom-tab root,
                    // same convention every other tab-root screen (Home/Local/...) already
                    // follows: no back arrow, just the bottom bar for cross-tab navigation.
                    onBack?.let { back ->
                        IconButton(onClick = back) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                },
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
                        ChatRoomRow(room = room, onClick = { onOpenRoom(room) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatRoomRow(room: ChatRoom, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Text(text = room.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
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
}
