package pl.larpnet.android.push

import android.content.Context
import android.util.Log
import kotlinx.coroutines.withTimeoutOrNull
import pl.larpnet.android.R
import pl.larpnet.android.data.matrix.ChatNotificationContent
import pl.larpnet.android.data.matrix.MatrixRepository

/**
 * Turns one chat push (room id + event id, from FCM or the ntfy-delivered web pusher) into a
 * notification. Decrypts the real sender/text on-device via
 * [MatrixRepository.notificationPreview]; if that fails or takes too long, still shows a generic
 * "new message" notification for the room -- a chat push must never silently vanish (the old
 * behaviour on any decrypt error). That fallback is a placeholder: at most one per room, and
 * replaced by the real text if the same event is pushed again (see [ChatNotificationStore]).
 */
object ChatPush {
    /** Well inside FCM's ~10 s budget for handling a high-priority data message. */
    private const val DECRYPT_TIMEOUT_MS = 7000L

    suspend fun show(context: Context, repository: MatrixRepository, roomId: String, eventId: String, fallbackTitle: String? = null) {
        // A chat push's one-shot notificationPreview() decrypt below never touches the
        // continuous sync loop the open/resumed chat screen actually depends on -- fire a fresh
        // sync request now, while FCM's delivery has briefly lifted Doze's network restrictions,
        // so a stalled loop gets a real chance to catch up instead of silently staying stale.
        // Unconditional (even if this room is the visible one, see the early return right
        // below): the open thread's own Timeline listener is fed by that same loop.
        repository.triggerCatchUpSync()

        if (roomId == PushNotifications.visibleChatRoomId) return

        val startedAt = System.currentTimeMillis()
        val result = withTimeoutOrNull(DECRYPT_TIMEOUT_MS) {
            runCatching { repository.notificationPreview(roomId, eventId) }
        }
        val elapsed = System.currentTimeMillis() - startedAt
        if (result != null && result.isSuccess) {
            val preview = result.getOrNull()
            Log.i("ChatPush", "room=$roomId event=$eventId decrypt=${if (preview == null) "filtered" else "ok"} ms=$elapsed")
            // null = nothing worth showing (redacted, a room we left, ...).
            preview?.let { PushNotifications.postChatMessage(context, it) }
            return
        }
        Log.w(
            "ChatPush",
            "room=$roomId event=$eventId decrypt=${if (result == null) "timeout" else "failed"} ms=$elapsed, showing a placeholder",
            result?.exceptionOrNull(),
        )
        PushNotifications.postChatMessage(
            context,
            ChatNotificationContent(
                roomId = roomId,
                eventId = eventId,
                senderName = fallbackTitle ?: context.getString(R.string.app_name),
                roomName = null,
                body = context.getString(R.string.chat_notification_fallback),
                placeholder = true,
            ),
        )
    }
}
