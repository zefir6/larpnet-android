package pl.larpnet.android.push

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Request
import pl.larpnet.android.App
import pl.larpnet.android.data.model.PushConfig
import pl.larpnet.android.di.AppContainer

/**
 * Holds a long-lived HTTP connection to the larpnet ntfy relay's `/<topic>/json` streaming
 * endpoint and turns each `event: "message"` line into a system notification. Runs as a
 * foreground service (self-contained delivery, no UnifiedPush distributor dependency --
 * see the app's push design decision) with a low-priority, silent "listening" notification.
 *
 * Not battery-optimal (a persistent socket vs. OS-scheduled delivery), but works on first
 * launch for a sideloaded APK with no other app installed. Known limitation: no boot receiver,
 * so a rebooted device won't resume listening until the app is opened again.
 */
class NtfyListenerService : Service() {

    private lateinit var appContainer: AppContainer
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var currentCall: Call? = null

    @Volatile
    private var loopStarted = false

    override fun onCreate() {
        super.onCreate()
        appContainer = (application as App).appContainer
        startForeground(PushNotifications.SERVICE_NOTIFICATION_ID, PushNotifications.serviceNotification(this))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!loopStarted) {
            loopStarted = true
            scope.launch { listenLoop() }
        }
        return START_STICKY
    }

    private suspend fun listenLoop() {
        var backoffSeconds = 5L
        while (true) {
            val config = appContainer.pushRepository.config().getOrNull()
            if (config == null || !config.enabled || config.ntfyTopic.isBlank()) {
                delay(60_000)
                continue
            }
            val cleanDisconnect = runCatching { streamOnce(config) }.getOrDefault(false)
            backoffSeconds = if (cleanDisconnect) 5L else (backoffSeconds * 2).coerceAtMost(60L)
            delay(backoffSeconds * 1000)
        }
    }

    /** Blocks the calling (IO-dispatcher) thread for the lifetime of the connection. Returns true on a clean server-initiated close. */
    private fun streamOnce(config: PushConfig): Boolean {
        val url = "${config.ntfyUrl.trimEnd('/')}/${config.ntfyTopic}/json"
        val requestBuilder = Request.Builder().url(url)
        if (config.ntfyToken.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${config.ntfyToken}")
        }
        val call = appContainer.pushOkHttpClient.newCall(requestBuilder.build())
        currentCall = call
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) return false
                val source = response.body?.source() ?: return false
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isBlank()) continue
                    val msg = runCatching { json.decodeFromString(NtfyMessage.serializer(), line) }.getOrNull() ?: continue
                    if (msg.event == "message") {
                        PushNotifications.postMessage(this, msg)
                    }
                }
            }
            return true
        } finally {
            currentCall = null
        }
    }

    override fun onDestroy() {
        currentCall?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            context.startForegroundService(Intent(context, NtfyListenerService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NtfyListenerService::class.java))
        }
    }
}
