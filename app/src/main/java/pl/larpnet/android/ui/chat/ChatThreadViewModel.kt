package pl.larpnet.android.ui.chat

import android.util.Log
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
    val roomId: String? = null,
    val roomName: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    /** Whether this room has more than one other member -- drives whether message clusters
     * show a sender avatar/name (group chats only; a 1:1's incoming sender is already obvious
     * from the room itself). */
    val isGroup: Boolean = false,
    /** Header avatar: the room's own `mxc://` avatar (groups) or the 1:1 partner's, plus the
     * partner's mxid for the Friendica-photo lookup (see `MatrixAvatarImage`). */
    val avatarUrl: String? = null,
    val peerUserId: String? = null,
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

    /** Whether the thread screen is resumed (in the foreground) -- read receipts are only sent
     * then, so a message that arrives while the app is in the background stays unread. */
    private var isVisible = false
    private var lastReadMessageId: String? = null
    private var isMarkingRead = false

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
                        // Opened from a chat notification, which only knows the room id.
                        if (target.name.isBlank()) {
                            runCatching { repository.roomDisplayName(target.id) }.getOrNull()
                                ?.let { uiState = uiState.copy(roomName = it) }
                        }
                        target.id
                    }
                    is ChatThreadTarget.Nickname -> {
                        uiState = uiState.copy(roomName = target.nickname)
                        val roomId = repository.openOrCreateDirectRoom(target.nickname)
                        // Placeholder only: the nickname is all a profile's "Chat" button knows.
                        // Swap in the same name the room list shows (full name by default).
                        runCatching { repository.roomDisplayName(roomId) }.getOrNull()
                            ?.let { uiState = uiState.copy(roomName = it) }
                        roomId
                    }
                }
                val info = runCatching { repository.roomInfo(roomId) }.getOrNull()
                val isGroup = info?.isGroup ?: false
                val peer = info?.members?.singleOrNull()?.takeIf { !isGroup }
                uiState = uiState.copy(avatarUrl = info?.avatarUrl ?: peer?.avatarUrl, peerUserId = peer?.userId)
                val newHandle = repository.openTimeline(roomId)
                handle = newHandle
                uiState = uiState.copy(roomId = roomId, isGroup = isGroup, isLoading = false)
                // Suspends for the lifetime of this screen -- viewModelScope is cancelled in
                // onCleared(), which is what actually ends this collection.
                newHandle.messages.collect { snapshot ->
                    uiState = uiState.copy(messages = snapshot)
                    markReadIfVisible()
                }
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false, error = e.message)
            }
        }
    }

    /** Called by the screen on resume/pause. */
    fun onVisibilityChanged(visible: Boolean) {
        isVisible = visible
        if (visible) {
            lastReadMessageId = null
            markReadIfVisible()
            // The sync loop can sit stalled after a backgrounded spell (Doze); resuming this
            // screen is exactly the moment a reply might be missing, so kick a fresh request
            // rather than trusting whatever cadence the existing loop resumes at on its own.
            // Fire-and-forget: the already-rendered cached state stays on screen either way,
            // this just makes a real update arrive sooner once it lands via the Timeline
            // listener this screen is already subscribed to.
            repository.triggerCatchUpSync()
        }
    }

    /**
     * Read receipt for the newest message, once per new message while on screen. Deliberately
     * does *not* set [lastReadMessageId] until [ChatTimelineHandle.sendReadReceipt] actually
     * succeeds: marking it read locally before the server confirms would permanently poison the
     * `newest == lastReadMessageId` guard above on a transient failure (network hiccup, sync
     * loop mid-restart) -- the room's unread bubble would then never clear until a newer message
     * bumped `newest` to a different id, exactly the "opening the conversation doesn't clear the
     * badge" symptom reported on iOS 2026-10-17 and fixed there the same way.
     */
    private fun markReadIfVisible() {
        if (!isVisible || isMarkingRead || uiState.roomId == null) return
        val newest = uiState.messages.lastOrNull { it.eventId != null }?.eventId ?: return
        if (newest == lastReadMessageId) return
        val activeHandle = handle ?: return
        isMarkingRead = true
        viewModelScope.launch {
            try {
                activeHandle.sendReadReceipt(newest)
                lastReadMessageId = newest
            } catch (e: Exception) {
                // lastReadMessageId left unset on purpose -- see doc comment above. The next
                // message update (or re-opening the thread) retries.
                Log.w("ChatThreadViewModel", "read receipt failed", e)
            } finally {
                isMarkingRead = false
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
