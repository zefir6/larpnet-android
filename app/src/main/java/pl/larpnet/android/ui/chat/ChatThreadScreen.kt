package pl.larpnet.android.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import pl.larpnet.android.R
import pl.larpnet.android.data.matrix.ChatMessage
import pl.larpnet.android.di.rememberAppContainer
import pl.larpnet.android.ui.theme.larpnetTopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatThreadScreen(
    target: ChatThreadTarget,
    onBack: () -> Unit,
    onOpenInfo: (String) -> Unit,
) {
    val appContainer = rememberAppContainer()
    val viewModelKey = when (target) {
        is ChatThreadTarget.Room -> "chat_thread_room_${target.id}"
        is ChatThreadTarget.Nickname -> "chat_thread_nickname_${target.nickname}"
    }
    val viewModel: ChatThreadViewModel = viewModel(
        key = viewModelKey,
        factory = viewModelFactory {
            initializer { ChatThreadViewModel(target, appContainer.matrixRepository) }
        },
    )
    val state = viewModel.uiState
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = larpnetTopAppBarColors(),
                title = { Text(state.roomName ?: stringResource(R.string.chat_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    state.roomId?.let { roomId ->
                        IconButton(onClick = { onOpenInfo(roomId) }) {
                            Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.chat_room_info))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.isLoading && state.messages.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.messages, key = { it.id }) { message ->
                            ChatMessageBubble(message)
                        }
                    }
                }
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.draft,
                    onValueChange = viewModel::onDraftChange,
                    placeholder = { Text(stringResource(R.string.message_hint)) },
                    modifier = Modifier.weight(1f),
                )
                if (state.isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(start = 8.dp))
                } else {
                    IconButton(onClick = viewModel::send, enabled = state.draft.isNotBlank()) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.message_send))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isOwn) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(
                    if (message.isOwn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(text = message.body, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
