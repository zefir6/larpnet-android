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
 * to begin with). onMessageReceived only fires while the app is foregrounded; a backgrounded
 * or killed app has the notification block auto-displayed by the system instead, using the
 * default icon/channel meta-data declared in AndroidManifest.xml.
 */
class LarpnetFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val appContainer = (application as App).appContainer
        CoroutineScope(Dispatchers.IO).launch {
            PushControl.onTokenRefreshed(appContainer, token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification ?: return
        PushNotifications.postMessage(
            context = this,
            requestCode = (message.messageId ?: notification.title.orEmpty()).hashCode(),
            title = notification.title,
            body = notification.body,
            click = message.data["click"],
        )
    }
}
