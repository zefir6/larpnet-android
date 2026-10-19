package pl.larpnet.android.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pl.larpnet.android.App

/**
 * Receives FCM pushes -- Play Store build only, see BuildConfig.FCM_PUSH_ENABLED and
 * PushControl.kt (main's ntfy-based build never enables auto-init, so this never gets a token
 * to begin with). Two independent kinds of message land here, both delivered to the same FCM
 * token/app:
 *
 * - Classic Friendica notifications (larpnet_fcm), which include a `notification` block.
 *   `onMessageReceived` only fires for these while the app is foregrounded -- a backgrounded
 *   or killed app has the block auto-displayed by the system instead, using the default
 *   icon/channel meta-data declared in AndroidManifest.xml.
 * - Matrix chat message pushes (larpnet_matrix's push gateway), which are data-only (no
 *   `notification` block, by design -- see that gateway's own doc comment for why real message
 *   content must never reach FCM). Unlike the above, FCM always calls `onMessageReceived` for a
 *   data-only message regardless of foreground/background/killed state -- that's specifically
 *   why this path was chosen over a `notification`-block push for chat: decryption has to
 *   happen on-device before anything is shown, which a system-auto-displayed notification can
 *   never do.
 */
class LarpnetFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val appContainer = (application as App).appContainer
        CoroutineScope(Dispatchers.IO).launch {
            PushControl.onTokenRefreshed(appContainer, token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification
        if (notification != null) {
            PushNotifications.postMessage(
                context = this,
                requestCode = (message.messageId ?: notification.title.orEmpty()).hashCode(),
                title = notification.title,
                body = notification.body,
                click = message.data["click"],
            )
            return
        }

        val roomId = message.data["room_id"]
        val eventId = message.data["event_id"]
        // Empty event_id = a "badge-only" push, `type=chat_counts` (counts changed after a read
        // receipt, i.e. something was read, possibly on another device). Never shown; it only
        // removes notifications of chats that are read now.
        if (eventId.isNullOrEmpty()) {
            ChatCounts.handle(this, (application as App).appContainer.matrixRepository, message.data["unread"]?.toIntOrNull())
            return
        }
        if (roomId.isNullOrEmpty()) return

        val appContainer = (application as App).appContainer
        CoroutineScope(Dispatchers.IO).launch {
            ChatPush.show(this@LarpnetFirebaseMessagingService, appContainer.matrixRepository, roomId, eventId)
        }
    }
}
