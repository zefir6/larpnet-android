package pl.larpnet.android.ui.chat

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.larpnet.android.R
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
    /** Set instead of [error] for a failure with a fixed, localized message. */
    val errorRes: Int? = null,
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
        uiState = uiState.copy(isSending = true, error = null, errorRes = null)
        viewModelScope.launch {
            try {
                activeHandle.send(text)
                uiState = uiState.copy(draft = "", isSending = false)
            } catch (e: Exception) {
                uiState = uiState.copy(isSending = false, error = e.message)
            }
        }
    }

    /** Sends a photo from the picker as `m.image`: re-encoded as JPEG and downscaled to at most
     * [MAX_IMAGE_SIDE] px (a full-resolution camera photo is 5-10 MB of mostly wasted upload),
     * then checked against the server's upload limit the way the web composer does. */
    fun sendImage(contentResolver: ContentResolver, uri: Uri) {
        val activeHandle = handle ?: return
        if (uiState.isSending) return
        uiState = uiState.copy(isSending = true, error = null, errorRes = null)
        viewModelScope.launch {
            try {
                val jpeg = withContext(Dispatchers.IO) { encodeJpeg(contentResolver, uri) }
                if (jpeg == null) {
                    uiState = uiState.copy(isSending = false, errorRes = R.string.chat_image_unreadable)
                    return@launch
                }
                val limit = runCatching { repository.maxUploadSize() }.getOrNull()
                if (limit != null && jpeg.bytes.size > limit) {
                    uiState = uiState.copy(isSending = false, errorRes = R.string.chat_image_too_large)
                    return@launch
                }
                activeHandle.sendImage(jpeg.bytes, jpeg.width, jpeg.height)
                uiState = uiState.copy(isSending = false)
            } catch (e: Exception) {
                uiState = uiState.copy(isSending = false, error = e.message)
            }
        }
    }

    private class EncodedImage(val bytes: ByteArray, val width: Int, val height: Int)

    private fun encodeJpeg(contentResolver: ContentResolver, uri: Uri): EncodedImage? = runCatching {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            decodeWithImageDecoder(contentResolver, uri)
        } else {
            decodeWithBitmapFactory(contentResolver, uri)
        } ?: return@runCatching null
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        EncodedImage(out.toByteArray(), bitmap.width, bitmap.height).also { bitmap.recycle() }
    }.getOrNull()

    /** `ImageDecoder` applies EXIF rotation and downsamples while decoding, so the JPEG comes out
     * upright (not every Matrix client honours EXIF orientation) without first decoding a
     * full-size bitmap. */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeWithImageDecoder(contentResolver: ContentResolver, uri: Uri): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > MAX_IMAGE_SIDE) {
                val factor = MAX_IMAGE_SIDE.toFloat() / longest
                decoder.setTargetSize((info.size.width * factor).toInt(), (info.size.height * factor).toInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }

    /** Android 8.x fallback: power-of-two downsampling only, and no EXIF rotation. */
    private fun decodeWithBitmapFactory(contentResolver: ContentResolver, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    override fun onCleared() {
        super.onCleared()
        handle?.close()
    }
}

private const val MAX_IMAGE_SIDE = 2048
