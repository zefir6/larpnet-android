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
        if (roomId == null || eventId == null) return

        val appContainer = (application as App).appContainer
        CoroutineScope(Dispatchers.IO).launch {
            // Best-effort: a decrypt failure (room left since, event redacted, transient
            // network error) must silently drop this push, never crash the receiver -- there
            // is nothing else useful to do with it, and Synapse doesn't expect a response
            // back through this path anyway (unlike the push gateway's own HTTP response to
            // Synapse, which is a separate, unrelated request/response pair on the server).
            val preview = runCatching { appContainer.matrixRepository.notificationPreview(roomId, eventId) }.getOrNull()
            val (title, body) = preview ?: return@launch
            PushNotifications.postMessage(
                context = this@LarpnetFirebaseMessagingService,
                requestCode = eventId.hashCode(),
                title = title,
                body = body,
                click = null,
            )
        }
    }
}
