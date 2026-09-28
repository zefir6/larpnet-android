package pl.larpnet.android.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.launch
import pl.larpnet.android.R
import pl.larpnet.android.data.matrix.ChatMessage
import pl.larpnet.android.data.matrix.MatrixRepository
import pl.larpnet.android.di.rememberAppContainer
import pl.larpnet.android.ui.common.MatrixAvatarImage
import pl.larpnet.android.ui.theme.LarpnetAccent
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
    val coroutineScope = rememberCoroutineScope()
    val groups = remember(state.messages) { ChatMessageGrouping.build(state.messages) }

    // Flattened row list backing the LazyColumn -- lets us scroll-to-end by a plain last-index
    // rather than hand-tracking per-section/per-cluster indices.
    val rows = remember(groups) {
        buildList {
            groups.forEach { day ->
                add(ChatRow.DaySeparator(day.dayStartMillis))
                day.clusters.forEach { add(ChatRow.ClusterRow(it)) }
            }
        }
    }

    var lastMessageCount by remember { mutableIntStateOf(0) }
    var pendingNewMessages by remember { mutableIntStateOf(0) }
    val isAtBottom by remember { derivedStateOf { !listState.canScrollForward } }

    LaunchedEffect(state.messages.size) {
        val newCount = state.messages.size
        if (newCount > lastMessageCount && rows.isNotEmpty()) {
            val justSentOwnMessage = state.messages.lastOrNull()?.isOwn == true
            if (isAtBottom || justSentOwnMessage) {
                listState.animateScrollToItem(rows.size - 1)
                pendingNewMessages = 0
            } else {
                pendingNewMessages += newCount - lastMessageCount
            }
        }
        lastMessageCount = newCount
    }
    LaunchedEffect(isAtBottom) {
        if (isAtBottom) pendingNewMessages = 0
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

                    !state.isLoading && state.messages.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.chat_thread_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        rows.forEach { row ->
                            when (row) {
                                is ChatRow.DaySeparator -> item(key = "day_${row.dayStartMillis}") {
                                    DaySeparator(row.dayStartMillis)
                                }
                                is ChatRow.ClusterRow -> item(key = "cluster_${row.cluster.id}") {
                                    ClusterView(row.cluster, isGroup = state.isGroup, repository = appContainer.matrixRepository)
                                }
                            }
                        }
                    }
                }

                if (pendingNewMessages > 0) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                if (rows.isNotEmpty()) listState.animateScrollToItem(rows.size - 1)
                            }
                            pendingNewMessages = 0
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LarpnetAccent),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                    ) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.chat_new_messages, pendingNewMessages))
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

private sealed class ChatRow {
    data class DaySeparator(val dayStartMillis: Long) : ChatRow()
    data class ClusterRow(val cluster: ChatMessageGrouping.Cluster) : ChatRow()
}

@Composable
private fun DaySeparator(dayStartMillis: Long) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = ChatMessageGrouping.dayLabel(dayStartMillis),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ClusterView(cluster: ChatMessageGrouping.Cluster, isGroup: Boolean, repository: MatrixRepository) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (cluster.isOwn) Arrangement.End else Arrangement.Start,
    ) {
        if (!cluster.isOwn) {
            if (isGroup) {
                MatrixAvatarImage(
                    avatarUrl = cluster.senderAvatarUrl, name = cluster.senderDisplayName ?: "?",
                    repository = repository, size = 28.dp,
                )
            } else {
                Spacer(modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        Column(
            horizontalAlignment = if (cluster.isOwn) Alignment.End else Alignment.Start,
            modifier = Modifier.wrapContentWidth(),
        ) {
            if (!cluster.isOwn && isGroup && cluster.senderDisplayName != null) {
                Text(
                    text = cluster.senderDisplayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            cluster.messages.forEachIndexed { index, message ->
                ChatMessageBubble(message, isLastInCluster = index == cluster.messages.size - 1)
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(message: ChatMessage, isLastInCluster: Boolean) {
    Column(horizontalAlignment = if (message.isOwn) Alignment.End else Alignment.Start) {
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
        if (isLastInCluster) {
            Text(
                text = remember(message.timestampMillis) {
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(message.timestampMillis)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}
