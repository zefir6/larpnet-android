package pl.larpnet.android.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pl.larpnet.android.data.matrix.ChatRoomMember
import pl.larpnet.android.data.matrix.MatrixRepository

data class ChatRoomInfoUiState(
    val isLoading: Boolean = false,
    val isBusy: Boolean = false,
    val members: List<ChatRoomMember> = emptyList(),
    val isGroup: Boolean = false,
    val rawName: String = "",
    val nameInput: String = "",
    val didLeave: Boolean = false,
    val error: String? = null,
)

/**
 * Direct port of the web client's `RoomInfoModal.jsx`: member list with remove, "add member"
 * (delegated to the caller via [pl.larpnet.android.ui.nav.Routes.ADD_CHAT_MEMBER], same split
 * as `ChatScreen`'s `onNewChat`), rename (group rooms only -- see [ChatRoomInfoUiState.isGroup]'s
 * use in [ChatRoomInfoScreen]), and leave (removing yourself).
 */
class ChatRoomInfoViewModel(
    private val roomId: String,
    private val repository: MatrixRepository,
) : ViewModel() {

    var uiState by mutableStateOf(ChatRoomInfoUiState())
        private set

    init {
        load()
    }

    fun load() {
        uiState = uiState.copy(isLoading = true)
        viewModelScope.launch {
            try {
                val info = repository.roomInfo(roomId)
                uiState = uiState.copy(
                    isLoading = false,
                    members = info.members,
                    isGroup = info.isGroup,
                    rawName = info.rawName,
                    nameInput = info.rawName,
                    error = null,
                )
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun onNameInputChange(value: String) {
        uiState = uiState.copy(nameInput = value)
    }

    fun rename() {
        val trimmed = uiState.nameInput.trim()
        if (trimmed.isEmpty() || trimmed == uiState.rawName) return
        uiState = uiState.copy(isBusy = true)
        viewModelScope.launch {
            try {
                repository.renameRoom(roomId, trimmed)
                uiState = uiState.copy(isBusy = false, rawName = trimmed, error = null)
            } catch (e: Exception) {
                uiState = uiState.copy(isBusy = false, error = e.message)
            }
        }
    }

    fun remove(userId: String) {
        uiState = uiState.copy(isBusy = true)
        viewModelScope.launch {
            try {
                repository.removeMember(roomId, userId)
                uiState = uiState.copy(isBusy = false, error = null)
                load()
            } catch (e: Exception) {
                uiState = uiState.copy(isBusy = false, error = e.message)
            }
        }
    }

    fun leave() {
        uiState = uiState.copy(isBusy = true)
        viewModelScope.launch {
            try {
                repository.leaveRoom(roomId)
                uiState = uiState.copy(isBusy = false, didLeave = true, error = null)
            } catch (e: Exception) {
                uiState = uiState.copy(isBusy = false, error = e.message)
            }
        }
    }
}
