package pl.larpnet.android.push

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pl.larpnet.android.BuildConfig
import pl.larpnet.android.di.AppContainer

/**
 * One push transport or the other, never both -- BuildConfig.FCM_PUSH_ENABLED picks which
 * (true only on the play-store branch, see app/build.gradle.kts). Callers (NavGraph,
 * SettingsScreen) go through here instead of touching NtfyListenerService/Firebase directly,
 * so they don't need to know which build they're in.
 */
object PushControl {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start(context: Context, appContainer: AppContainer) {
        if (BuildConfig.FCM_PUSH_ENABLED) {
            scope.launch { registerFcmToken(appContainer) }
        } else {
            NtfyListenerService.start(context)
        }
    }

    fun stop(context: Context, appContainer: AppContainer) {
        if (BuildConfig.FCM_PUSH_ENABLED) {
            scope.launch { unregisterFcmToken(appContainer) }
        } else {
            NtfyListenerService.stop(context)
        }
    }

    /** Called from LarpnetFirebaseMessagingService.onNewToken, already off the main thread. */
    suspend fun onTokenRefreshed(appContainer: AppContainer, token: String) {
        if (!appContainer.tokenStore.isLoggedIn || !appContainer.tokenStore.pushEnabled) return
        appContainer.pushRepository.registerFcmToken(token)
    }

    private suspend fun registerFcmToken(appContainer: AppContainer) {
        // Auto-init is off by default (AndroidManifest.xml meta-data) so the ntfy-based build
        // never has Firebase silently phoning home for a token nobody asked for. Turning it on
        // here means Firebase also keeps renewing the token on its own from now on, delivered
        // via onNewToken above.
        FirebaseMessaging.getInstance().isAutoInitEnabled = true
        val token = runCatching { Tasks.await(FirebaseMessaging.getInstance().token) }.getOrNull() ?: return
        appContainer.pushRepository.registerFcmToken(token)
    }

    private suspend fun unregisterFcmToken(appContainer: AppContainer) {
        val token = runCatching { Tasks.await(FirebaseMessaging.getInstance().token) }.getOrNull()
        if (token != null) {
            appContainer.pushRepository.unregisterFcmToken(token)
        }
        FirebaseMessaging.getInstance().isAutoInitEnabled = false
    }
}
