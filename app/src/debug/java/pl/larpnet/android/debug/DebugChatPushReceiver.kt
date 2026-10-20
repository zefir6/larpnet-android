package pl.larpnet.android.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pl.larpnet.android.App
import pl.larpnet.android.push.ChatPush

/**
 * Debug builds only: feeds a real room/event into [ChatPush.show], exactly as an FCM/ntfy chat
 * push would, so the notification path can be exercised on an emulator without a working push
 * transport (an emulator's Play services is often too old for FCM).
 *
 * ```
 * adb shell am broadcast -a pl.larpnet.android.DEBUG_CHAT_PUSH -p pl.larpnet.android --ez list true
 * adb shell am broadcast -a pl.larpnet.android.DEBUG_CHAT_PUSH -p pl.larpnet.android --es room '!id' [--ei nth 0] [--es event '$id']
 * ```
 * `list` logs room ids/names and the latest incoming event ids per room (tag `DebugChatPush`);
 * without `event`, the `nth` newest incoming message (default 0 = newest) is pushed.
 */
class DebugChatPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as App
        val repository = app.appContainer.matrixRepository
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (intent.getBooleanExtra("list", false)) {
                    for (room in repository.rooms()) {
                        Log.i(TAG, "room ${room.id} name=${room.name} unread=${room.unreadCount}")
                    }
                    return@launch
                }
                val roomId = intent.getStringExtra("room") ?: return@launch
                val eventId = intent.getStringExtra("event") ?: run {
                    val handle = repository.openTimeline(roomId)
                    try {
                        handle.messages.value.filter { !it.isOwn && it.eventId != null }
                            .reversed()
                            .getOrNull(intent.getIntExtra("nth", 0))
                            ?.eventId
                    } finally {
                        handle.close()
                    }
                } ?: run {
                    Log.w(TAG, "no incoming message found in $roomId")
                    return@launch
                }
                Log.i(TAG, "simulating push room=$roomId event=$eventId")
                ChatPush.show(app, repository, roomId, eventId)
            } catch (e: Exception) {
                Log.w(TAG, "debug push failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "DebugChatPush"
    }
}
