package pl.larpnet.android.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pl.larpnet.android.data.matrix.ChatRoom
import pl.larpnet.android.data.matrix.MatrixRepository

data class ChatUiState(
    val rooms: List<ChatRoom> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    /** null once resolved (nothing to show) -- see [MatrixRepository.recoveryPromptKind]. */
    val recoveryPrompt: MatrixRepository.RecoveryPromptKind? = null,
)

/**
 * Room list for native Matrix chat -- shaped like `ConversationsViewModel` (Friendica DMs),
 * but backed by [MatrixRepository] instead of the Mastodon-API conversations endpoint.
 * Refreshes live via [MatrixRepository.roomListUpdates] (fed by the SDK's own background sync
 * loop) rather than pull-to-refresh alone, since there's no Link-header pagination here to
 * drive a "load more" gesture off of.
 */
class ChatViewModel(private val repository: MatrixRepository) : ViewModel() {

    var uiState by mutableStateOf(ChatUiState())
        private set

    private var subscribedToUpdates = false

    init {
        refresh()
        subscribeToUpdates()
        checkRecovery()
    }

    fun dismissRecoveryPrompt() {
        uiState = uiState.copy(recoveryPrompt = null)
    }

    private fun checkRecovery() {
        viewModelScope.launch {
            val kind = runCatching { repository.recoveryPromptKind() }.getOrNull()
            uiState = uiState.copy(recoveryPrompt = kind)
        }
    }

    fun refresh() {
        uiState = uiState.copy(isLoading = uiState.rooms.isEmpty(), error = null)
        viewModelScope.launch {
            try {
                uiState = uiState.copy(rooms = repository.rooms(), isLoading = false)
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false, error = e.message)
            }
        }
    }

    private fun subscribeToUpdates() {
        if (subscribedToUpdates) return
        subscribedToUpdates = true
        viewModelScope.launch {
            repository.roomListUpdates.collect { refresh() }
        }
    }

    /** "Delete chat" -- Matrix has no server-side delete for a room's history, only leaving it
     * (per-member, standard Matrix semantics: your own local copy of the timeline stays
     * readable, but the room disappears from your list and you stop receiving new messages;
     * rejoining a 1:1 later starts a fresh room via [MatrixRepository.openOrCreateDirectRoom]).
     * Removes the row optimistically so the swipe action feels immediate rather than waiting on
     * the leave round-trip; [refresh] (also driven by the live room-list update this triggers)
     * is the source of truth if it fails. */
    fun leaveRoom(room: ChatRoom) {
        uiState = uiState.copy(rooms = uiState.rooms.filterNot { it.id == room.id })
        viewModelScope.launch {
            try {
                repository.leaveRoom(room.id)
                uiState = uiState.copy(error = null)
            } catch (e: Exception) {
                uiState = uiState.copy(error = e.message)
                refresh()
            }
        }
    }
}
