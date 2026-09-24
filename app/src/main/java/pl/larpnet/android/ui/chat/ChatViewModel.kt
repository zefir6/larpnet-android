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
