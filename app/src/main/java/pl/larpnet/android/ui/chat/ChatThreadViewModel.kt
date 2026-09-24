package pl.larpnet.android.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pl.larpnet.android.data.matrix.ChatMessage
import pl.larpnet.android.data.matrix.ChatTimelineHandle
import pl.larpnet.android.data.matrix.MatrixRepository

/**
 * Either an already-known room (opened from the room list, which already has its display
 * name) or a bare Friendica nickname (opened from a profile's "Chat" button or the new-chat
 * picker) -- [ChatThreadViewModel]'s init resolves the latter to a room id itself via
 * [MatrixRepository.openOrCreateDirectRoom], creating the room on first contact.
 */
sealed class ChatThreadTarget {
    data class Room(val id: String, val name: String) : ChatThreadTarget()
    data class Nickname(val nickname: String) : ChatThreadTarget()
}

data class ChatThreadUiState(
    val roomName: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val draft: String = "",
    val isSending: Boolean = false,
    val error: String? = null,
)

/**
 * Direct port of `ConversationThreadViewModel`'s shape for native Matrix chat: loads a room's
 * timeline, tracks a send box. Unlike Friendica DMs, the timeline here is a live subscription
 * ([ChatTimelineHandle.messages]), not a one-shot fetch -- [onCleared] stops it when this
 * ViewModel (and so the thread screen) goes away, mirroring larpnet-iOS's
 * `ChatThreadViewModel.close()` but tied to the platform's own lifecycle hook instead of a
 * Composable's `onDisappear`.
 */
class ChatThreadViewModel(
    private val target: ChatThreadTarget,
    private val repository: MatrixRepository,
) : ViewModel() {

    var uiState by mutableStateOf(ChatThreadUiState())
        private set

    private var handle: ChatTimelineHandle? = null

    init {
        load()
    }

    private fun load() {
        uiState = uiState.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val roomId = when (target) {
                    is ChatThreadTarget.Room -> {
                        uiState = uiState.copy(roomName = target.name)
                        target.id
                    }
                    is ChatThreadTarget.Nickname -> {
                        uiState = uiState.copy(roomName = target.nickname)
                        repository.openOrCreateDirectRoom(target.nickname)
                    }
                }
                val newHandle = repository.openTimeline(roomId)
                handle = newHandle
                uiState = uiState.copy(isLoading = false)
                // Suspends for the lifetime of this screen -- viewModelScope is cancelled in
                // onCleared(), which is what actually ends this collection.
                newHandle.messages.collect { snapshot ->
                    uiState = uiState.copy(messages = snapshot)
                }
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun onDraftChange(value: String) {
        uiState = uiState.copy(draft = value)
    }

    fun send() {
        val activeHandle = handle ?: return
        val text = uiState.draft.trim()
        if (text.isEmpty() || uiState.isSending) return
        uiState = uiState.copy(isSending = true, error = null)
        viewModelScope.launch {
            try {
                activeHandle.send(text)
                uiState = uiState.copy(draft = "", isSending = false)
            } catch (e: Exception) {
                uiState = uiState.copy(isSending = false, error = e.message)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        handle?.close()
    }
}
