package pl.larpnet.android.push

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import pl.larpnet.android.data.matrix.ChatNotificationContent
import pl.larpnet.android.MainActivity
import pl.larpnet.android.R
import java.util.concurrent.atomic.AtomicInteger

/** Notification channels + posting for ntfy-delivered push messages and the listener service's own status notification. */
object PushNotifications {
    const val CHANNEL_MESSAGES = "larpnet_push_messages"
    const val CHANNEL_SERVICE = "larpnet_push_service"
    /** Chat messages get their own high-importance channel (heads-up, like any messenger) instead
     * of sharing [CHANNEL_MESSAGES] with likes/follows -- and users can tune them separately. */
    const val CHANNEL_CHAT = "larpnet_chat"
    const val SERVICE_NOTIFICATION_ID = 1

    /** Intent extra carrying the Matrix room id a chat notification opens -- read by MainActivity. */
    const val EXTRA_CHAT_ROOM_ID = "pl.larpnet.android.CHAT_ROOM_ID"

    /** Tag for chat notifications; the id is the room id's hash, so one notification per room. */
    private const val CHAT_TAG = "chat"
    private const val CHAT_GROUP = "pl.larpnet.android.CHAT"

    /** Room currently on screen in the chat thread (set by ChatThreadScreen while resumed) --
     * a push for it is not shown at all, the user is already reading it. */
    @Volatile
    var visibleChatRoomId: String? = null

    private val nextMessageId = AtomicInteger(1000)

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MESSAGES,
                context.getString(R.string.push_channel_messages),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CHAT,
                context.getString(R.string.push_channel_chat),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SERVICE,
                context.getString(R.string.push_channel_service),
                NotificationManager.IMPORTANCE_MIN,
            ).apply { setShowBadge(false) },
        )
    }

    fun serviceNotification(context: Context): Notification {
        ensureChannels(context)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setContentTitle(context.getString(R.string.push_service_title))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    fun postMessage(context: Context, msg: NtfyMessage) {
        postMessage(context, requestCode = msg.id.hashCode(), title = msg.title, body = msg.message, click = null)
    }

    /** Shared by both push transports -- ntfy (above) and FCM (LarpnetFirebaseMessagingService).
     * [requestCode] should be unique per message, so each notification's tap action gets its
     * own PendingIntent instead of FLAG_UPDATE_CURRENT silently rewriting a shared one. */
    fun postMessage(context: Context, requestCode: Int, title: String?, body: String?, click: String?) {
        ensureChannels(context)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = body.orEmpty()
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setContentTitle(title ?: context.getString(R.string.app_name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(nextMessageId.incrementAndGet(), notification)
    }

    /**
     * Posts (or extends) the notification for one chat room. Messages from the same room stack
     * into a single MessagingStyle notification -- the earlier lines are read back from the one
     * already showing -- instead of one notification per message. Tapping it opens that room.
     * Shared by FCM (Play build) and the ntfy listener (GitHub build).
     */
    fun postChatMessage(context: Context, content: ChatNotificationContent) {
        if (content.roomId == visibleChatRoomId) return
        ensureChannels(context)
        val manager = NotificationManagerCompat.from(context)
        val notificationId = content.roomId.hashCode()

        val me = Person.Builder().setName(context.getString(R.string.app_name)).build()
        val active = context.getSystemService(NotificationManager::class.java)
            ?.activeNotifications
            ?.firstOrNull { it.tag == CHAT_TAG && it.id == notificationId }
            ?.notification
        val style = active?.let { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it) }
            ?: NotificationCompat.MessagingStyle(me)
        style.conversationTitle = content.roomName
        style.isGroupConversation = content.roomName != null
        style.addMessage(
            content.body,
            System.currentTimeMillis(),
            Person.Builder().setName(content.senderName).setKey(content.senderName).build(),
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_CHAT)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setStyle(style)
            .setContentTitle(content.roomName ?: content.senderName)
            .setContentText(content.body)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(CHAT_GROUP)
            .setAutoCancel(true)
            .setContentIntent(chatPendingIntent(context, content.roomId))
            .build()
        // POST_NOTIFICATIONS is a runtime permission from Android 13 on.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        manager.notify(CHAT_TAG, notificationId, notification)
    }

    /** Removes [roomId]'s chat notification -- called when the room is opened. */
    fun cancelChat(context: Context, roomId: String) {
        NotificationManagerCompat.from(context).cancel(CHAT_TAG, roomId.hashCode())
    }

    private fun chatPendingIntent(context: Context, roomId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_CHAT_ROOM_ID, roomId)
        }
        return PendingIntent.getActivity(
            context,
            roomId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
