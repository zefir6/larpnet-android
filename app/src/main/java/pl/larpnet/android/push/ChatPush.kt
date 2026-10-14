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
 * behaviour on any decrypt error).
 */
object ChatPush {
    /** Well inside FCM's ~10 s budget for handling a high-priority data message. */
    private const val DECRYPT_TIMEOUT_MS = 7000L

    suspend fun show(context: Context, repository: MatrixRepository, roomId: String, eventId: String, fallbackTitle: String? = null) {
        if (roomId == PushNotifications.visibleChatRoomId) return

        val result = withTimeoutOrNull(DECRYPT_TIMEOUT_MS) {
            runCatching { repository.notificationPreview(roomId, eventId) }
        }
        if (result != null && result.isSuccess) {
            // null = nothing worth showing (redacted, a room we left, ...).
            result.getOrNull()?.let { PushNotifications.postChatMessage(context, it) }
            return
        }
        Log.w("ChatPush", "couldn't decrypt chat push, showing a generic one", result?.exceptionOrNull())
        PushNotifications.postChatMessage(
            context,
            ChatNotificationContent(
                roomId = roomId,
                eventId = eventId,
                senderName = fallbackTitle ?: context.getString(R.string.app_name),
                roomName = null,
                body = context.getString(R.string.chat_notification_fallback),
            ),
        )
    }
}
