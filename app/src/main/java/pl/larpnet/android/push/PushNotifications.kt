package pl.larpnet.android.push

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import pl.larpnet.android.data.matrix.ChatNotificationContent
import pl.larpnet.android.MainActivity
import pl.larpnet.android.R

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

    /** Tag for classic Friendica notifications; the id is the push message's own id hash. */
    private const val SOCIAL_TAG = "social"
    private const val CHAT_SUMMARY_TAG = "chat_summary"
    private const val CHAT_SUMMARY_ID = 0
    private const val LOG_TAG = "PushNotifications"

    /** See [postChatMessage]'s dismissal check. */
    private const val DISMISS_CHECK_GRACE_MS = 10_000L

    /** See [reconcileChat]. */
    private const val RECONCILE_GRACE_MS = 15_000L

    /** Guards [store] together with the notifications it describes -- FCM handles pushes on
     * parallel coroutines, two for the same room must not interleave. */
    private val chatLock = Any()

    @Volatile
    private var store: ChatNotificationStore? = null

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
    @SuppressLint("MissingPermission") // checked via canPostNotifications()
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
        if (!canPostNotifications(context)) return
        // Same message delivered twice (FCM/ntfy redelivery) replaces itself instead of stacking.
        NotificationManagerCompat.from(context).notify(SOCIAL_TAG, requestCode, notification)
    }

    /**
     * Posts (or extends) the notification for one chat room: one MessagingStyle notification per
     * room, rendered from [ChatNotificationStore] rather than read back from the one already
     * showing, so a re-delivered push for an event already shown changes nothing (no extra line,
     * no second alert). Tapping it opens that room. Shared by FCM (Play build) and the ntfy
     * listener (GitHub build).
     */
    fun postChatMessage(context: Context, content: ChatNotificationContent) {
        if (content.roomId == visibleChatRoomId) return
        ensureChannels(context)
        if (!canPostNotifications(context)) return
        synchronized(chatLock) {
            val store = chatStore(context)
            val now = System.currentTimeMillis()
            // Dismissed by the user (swipe, "Clear all") or dropped by the system: start this
            // room afresh instead of resurrecting the old lines. The grace period covers a
            // notification posted moments ago that the system hasn't listed as active yet.
            val stored = store.get(content.roomId)
            if (stored != null && now - stored.updatedAtMillis > DISMISS_CHECK_GRACE_MS && !isChatShowing(context, content.roomId)) {
                store.remove(content.roomId)
            }
            val result = store.add(
                roomId = content.roomId,
                roomName = content.roomName,
                line = ChatNotificationStore.Line(
                    eventId = content.eventId,
                    senderName = content.senderName,
                    body = content.body,
                    timestampMillis = now,
                    placeholder = content.placeholder,
                ),
                nowMillis = now,
            )
            Log.i(LOG_TAG, "chat push room=${content.roomId} event=${content.eventId} placeholder=${content.placeholder} result=$result")
            if (result == ChatNotificationStore.AddResult.DUPLICATE) return
            // Swapping a placeholder for the real text is an update, not a new message.
            notifyChatRoom(context, content.roomId, silent = result == ChatNotificationStore.AddResult.REPLACED_PLACEHOLDER)
            updateChatSummary(context)
        }
    }

    /** Removes [roomId]'s chat notification -- called when the room is opened, read (read
     * receipt sent from this device) or muted. */
    fun cancelChat(context: Context, roomId: String) {
        synchronized(chatLock) {
            chatStore(context).remove(roomId)
            NotificationManagerCompat.from(context).cancel(CHAT_TAG, roomId.hashCode())
            updateChatSummary(context)
        }
    }

    /**
     * Drops the notification of every room that is no longer unread according to the latest
     * sync ([unreadRoomIds]: joined, not muted, Synapse notification count > 0) -- this is how a
     * room read on another device (web, iOS) or muted elsewhere disappears here too.
     *
     * Only rooms whose notification was last updated more than [RECONCILE_GRACE_MS] before
     * [snapshotAtMillis] are touched: a push can arrive before this device's own sync has seen
     * the event, and that sync's (still zero) count must not cancel the notification just posted.
     */
    fun reconcileChat(context: Context, unreadRoomIds: Set<String>, snapshotAtMillis: Long) {
        synchronized(chatLock) {
            val store = chatStore(context)
            val stale = store.all().filter { (roomId, entry) ->
                roomId !in unreadRoomIds && entry.updatedAtMillis < snapshotAtMillis - RECONCILE_GRACE_MS
            }.keys
            if (stale.isEmpty()) return
            val manager = NotificationManagerCompat.from(context)
            for (roomId in stale) {
                store.remove(roomId)
                manager.cancel(CHAT_TAG, roomId.hashCode())
            }
            Log.i(LOG_TAG, "reconcile cancelled ${stale.size} read room(s): $stale")
            updateChatSummary(context)
        }
    }

    @SuppressLint("MissingPermission") // checked via canPostNotifications()
    private fun notifyChatRoom(context: Context, roomId: String, silent: Boolean) {
        val entry = chatStore(context).get(roomId) ?: return
        val last = entry.lines.lastOrNull() ?: return
        val me = Person.Builder().setName(context.getString(R.string.app_name)).build()
        val style = NotificationCompat.MessagingStyle(me)
            .setConversationTitle(entry.roomName)
            .setGroupConversation(entry.roomName != null)
        for (line in entry.lines) {
            style.addMessage(
                line.body,
                line.timestampMillis,
                Person.Builder().setName(line.senderName).setKey(line.senderName).build(),
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_CHAT)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setStyle(style)
            .setContentTitle(entry.roomName ?: last.senderName)
            .setContentText(last.body)
            .setWhen(last.timestampMillis)
            .setNumber(entry.count)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(CHAT_GROUP)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setSilent(silent)
            .setAutoCancel(true)
            .setContentIntent(chatPendingIntent(context, roomId))
            .build()
        NotificationManagerCompat.from(context).notify(CHAT_TAG, roomId.hashCode(), notification)
    }

    /** The group summary bundling all chat rooms (required for proper grouping on Android 7+);
     * silent -- only the per-room notifications alert. Removed once no room is left. */
    @SuppressLint("MissingPermission") // checked via canPostNotifications()
    private fun updateChatSummary(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        val rooms = chatStore(context).all().values
            .filter { it.lines.isNotEmpty() }
            .sortedByDescending { it.updatedAtMillis }
        if (rooms.isEmpty()) {
            manager.cancel(CHAT_SUMMARY_TAG, CHAT_SUMMARY_ID)
            return
        }
        if (!canPostNotifications(context)) return
        val inbox = NotificationCompat.InboxStyle()
        for (room in rooms) {
            val last = room.lines.last()
            inbox.addLine("${room.roomName ?: last.senderName}: ${last.body}")
        }
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val summary = NotificationCompat.Builder(context, CHANNEL_CHAT)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setStyle(inbox)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(rooms.first().lines.last().body)
            .setNumber(rooms.sumOf { it.count })
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup(CHAT_GROUP)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(context, CHAT_SUMMARY_ID, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
            .build()
        manager.notify(CHAT_SUMMARY_TAG, CHAT_SUMMARY_ID, summary)
    }

    private fun isChatShowing(context: Context, roomId: String): Boolean =
        context.getSystemService(NotificationManager::class.java)
            ?.activeNotifications
            ?.any { it.tag == CHAT_TAG && it.id == roomId.hashCode() }
            ?: false

    /** POST_NOTIFICATIONS is a runtime permission from Android 13 on. */
    private fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun chatStore(context: Context): ChatNotificationStore =
        store ?: ChatNotificationStore(context.applicationContext).also { store = it }

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
