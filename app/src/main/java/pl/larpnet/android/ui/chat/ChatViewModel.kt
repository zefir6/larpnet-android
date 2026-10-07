package pl.larpnet.android.ui.chat

import android.util.Log
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
    /** null once resolved (nothing to show) -- see [MatrixRepository.ensureEncryption]. */
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
        uiState = uiState.copy(isLoading = true)
        viewModelScope.launch {
            // Runs once per session, before the first room-list load -- see its own doc comment
            // for why leftover duplicate DM rooms exist at all, and how it picks which one
            // survives.
            runCatching { repository.consolidateDuplicateDirectRooms() }
            refresh()
        }
        subscribeToUpdates()
        checkRecovery()
    }

    fun dismissRecoveryPrompt() {
        uiState = uiState.copy(recoveryPrompt = null)
    }

    private fun checkRecovery() {
        viewModelScope.launch {
            // Standard encryption mode unlocks silently with the server-held passphrase; only
            // private mode (or a legacy key on a locked device) yields a prompt here.
            val kind = runCatching { repository.ensureEncryption() }
                .onFailure { Log.w("ChatViewModel", "ensureEncryption failed", it) }
                .getOrNull()
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
}
