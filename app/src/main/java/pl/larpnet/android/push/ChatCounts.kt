package pl.larpnet.android.push

import android.content.Context
import android.util.Log
import kotlinx.coroutines.runBlocking
import pl.larpnet.android.data.matrix.MatrixRepository

/**
 * A `chat_counts` push: Synapse's unread counts changed after a read receipt -- something was
 * read, possibly on another device. Never shown; it only removes the notifications of chats
 * that are read now (larpnet_matrix's gateway sends it at normal priority).
 */
internal object ChatCounts {
    private const val LOG_TAG = "ChatCounts"
    /** FCM allows about 20 s per message; leave room for the rest. */
    private const val SYNC_TIMEOUT_MS = 10_000L

    /**
     * Blocks the calling (FCM worker) thread on purpose: Android freezes a backgrounded app again
     * as soon as the handler returns, so work launched elsewhere wouldn't run until the app is
     * next opened. Nothing unread anywhere ([unread] = 0) needs no sync -- every chat
     * notification older than the reconcile grace goes. Otherwise a short sync tells which rooms
     * are read now ([MatrixRepository.catchUpUnread] -> `PushNotifications.reconcileChat`).
     */
    fun handle(context: Context, repository: MatrixRepository, unread: Int?) {
        if (unread == 0) {
            Log.i(LOG_TAG, "chat_counts unread=0, clearing")
            PushNotifications.reconcileChat(context, emptySet(), System.currentTimeMillis())
            return
        }
        val reconciled = runCatching {
            runBlocking { repository.catchUpUnread(SYNC_TIMEOUT_MS) }
        }.onFailure { Log.w(LOG_TAG, "chat_counts catch-up failed", it) }.getOrDefault(false)
        Log.i(LOG_TAG, "chat_counts unread=$unread reconciled=$reconciled")
    }
}
