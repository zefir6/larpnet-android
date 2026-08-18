package pl.larpnet.android.push

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import pl.larpnet.android.MainActivity
import pl.larpnet.android.R
import java.util.concurrent.atomic.AtomicInteger

/** Notification channels + posting for ntfy-delivered push messages and the listener service's own status notification. */
object PushNotifications {
    const val CHANNEL_MESSAGES = "larpnet_push_messages"
    const val CHANNEL_SERVICE = "larpnet_push_service"
    const val SERVICE_NOTIFICATION_ID = 1

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
        ensureChannels(context)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            msg.id.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = msg.message.orEmpty()
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setContentTitle(msg.title ?: context.getString(R.string.app_name))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(nextMessageId.incrementAndGet(), notification)
    }
}
