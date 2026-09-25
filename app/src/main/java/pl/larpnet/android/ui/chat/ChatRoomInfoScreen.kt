package pl.larpnet.android.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import pl.larpnet.android.data.matrix.ChatRoomMember
import pl.larpnet.android.di.rememberAppContainer
import pl.larpnet.android.ui.theme.larpnetTopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomInfoScreen(
    roomId: String,
    onBack: () -> Unit,
    onAddMember: () -> Unit,
    onLeft: () -> Unit,
) {
    val appContainer = rememberAppContainer()
    val viewModel: ChatRoomInfoViewModel = viewModel(
        key = "chat_room_info_$roomId",
        factory = viewModelFactory {
            initializer { ChatRoomInfoViewModel(roomId, appContainer.matrixRepository) }
        },
    )
    val state = viewModel.uiState

    LaunchedEffect(state.didLeave) {
        if (state.didLeave) onLeft()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = larpnetTopAppBarColors(),
                title = { Text(stringResource(R.string.chat_room_info)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(padding)) {
            if (state.isGroup) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = state.nameInput,
                            onValueChange = viewModel::onNameInputChange,
                            placeholder = { Text(stringResource(R.string.chat_room_name_hint)) },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = viewModel::rename,
                            enabled = !state.isBusy && state.nameInput.isNotBlank() && state.nameInput != state.rawName,
                        ) {
                            Text(stringResource(R.string.chat_room_save))
                        }
                    }
                }
            }
            item {
                Text(
                    text = stringResource(R.string.chat_room_participants),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(state.members, key = { it.userId }) { member ->
                ChatRoomMemberRow(member = member, enabled = !state.isBusy, onRemove = { viewModel.remove(member.userId) })
            }
            item {
                TextButton(onClick = onAddMember, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(stringResource(R.string.chat_room_add_member))
                }
            }
            state.error?.let { error ->
                item {
                    Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                }
            }
            item {
                TextButton(
                    onClick = viewModel::leave,
                    enabled = !state.isBusy,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(stringResource(R.string.chat_room_leave), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ChatRoomMemberRow(member: ChatRoomMember, enabled: Boolean, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(member.displayName, style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onRemove, enabled = enabled) {
            Text(stringResource(R.string.chat_room_remove_member), color = MaterialTheme.colorScheme.error)
        }
    }
}
