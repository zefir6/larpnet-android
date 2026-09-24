package pl.larpnet.android.data.matrix

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.matrix.rustcomponents.sdk.MsgLikeKind
import org.matrix.rustcomponents.sdk.TaskHandle
import org.matrix.rustcomponents.sdk.Timeline
import org.matrix.rustcomponents.sdk.TimelineDiff
import org.matrix.rustcomponents.sdk.TimelineItem
import org.matrix.rustcomponents.sdk.TimelineItemContent
import org.matrix.rustcomponents.sdk.TimelineListener
import org.matrix.rustcomponents.sdk.messageEventContentFromMarkdown

/**
 * One open room's timeline. Applies `TimelineDiff`s to a running snapshot and republishes the
 * whole snapshot (not just the delta) via [messages] -- simple over clever, since a DM's
 * timeline is never large enough for delta-based list diffing to matter here the way it would
 * for Element X's general-purpose room list. Mirrors larpnet-iOS's `ChatTimelineHandle`.
 *
 * Each incoming `TimelineItem` is converted to a [ChatMessage]? (or discarded, if it's not a
 * plain message) and closed immediately -- see `MatrixRepository`'s doc comment on why raw
 * MatrixRustSDK FFI objects need explicit disposal on this platform, unlike the iOS/Swift
 * equivalent (ARC there frees them automatically). This means the internal snapshot list
 * never holds a live FFI reference, only plain values -- nothing here can leak native memory
 * by outliving a call to [close].
 *
 * [TimelineListener.onUpdate] is called by the SDK from its own worker thread, not necessarily
 * this repository's caller's thread -- [apply] is `@Synchronized` so a callback landing mid-way
 * through a previous one can't corrupt the snapshot list.
 */
class ChatTimelineHandle(private val timeline: Timeline) {
    private var listenerHandle: TaskHandle? = null
    private val items = mutableListOf<ChatMessage?>()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    suspend fun start() {
        listenerHandle = timeline.addListener(
            object : TimelineListener {
                override fun onUpdate(diff: List<TimelineDiff>) {
                    apply(diff)
                }
            },
        )
    }

    suspend fun send(text: String) {
        timeline.send(messageEventContentFromMarkdown(text))
    }

    /** Stops the timeline listener and frees the underlying `Timeline` -- call when the
     * thread screen closes, so this room's diff subscription doesn't keep running (and
     * retaining native memory) after the view that cares about it is gone. */
    fun close() {
        listenerHandle?.cancel()
        listenerHandle?.close()
        timeline.close()
    }

    @Synchronized
    private fun apply(diffs: List<TimelineDiff>) {
        for (diff in diffs) {
            when (diff) {
                is TimelineDiff.Append -> items.addAll(diff.values.map(::toChatMessage))
                is TimelineDiff.Clear -> items.clear()
                is TimelineDiff.PushFront -> items.add(0, toChatMessage(diff.value))
                is TimelineDiff.PushBack -> items.add(toChatMessage(diff.value))
                is TimelineDiff.PopFront -> if (items.isNotEmpty()) items.removeAt(0)
                is TimelineDiff.PopBack -> if (items.isNotEmpty()) items.removeAt(items.size - 1)
                is TimelineDiff.Insert -> items.add(diff.index.toInt(), toChatMessage(diff.value))
                is TimelineDiff.Set -> items[diff.index.toInt()] = toChatMessage(diff.value)
                is TimelineDiff.Remove -> items.removeAt(diff.index.toInt())
                is TimelineDiff.Truncate -> while (items.size > diff.length.toInt()) items.removeAt(items.size - 1)
                is TimelineDiff.Reset -> {
                    items.clear()
                    items.addAll(diff.values.map(::toChatMessage))
                }
            }
        }
        _messages.value = items.filterNotNull()
    }

    /** Converts (and immediately closes, freeing the native object) one `TimelineItem`. */
    private fun toChatMessage(item: TimelineItem): ChatMessage? {
        try {
            val event = item.asEvent() ?: return null
            val msgLike = (event.content as? TimelineItemContent.MsgLike)?.content ?: return null
            val body = when (val kind = msgLike.kind) {
                is MsgLikeKind.Message -> kind.content.body
                is MsgLikeKind.UnableToDecrypt -> "🔒"
                else -> return null
            }
            return ChatMessage(
                id = item.uniqueId().id,
                isOwn = event.isOwn,
                body = body,
                timestampMillis = event.timestamp.toLong(),
            )
        } finally {
            item.close()
        }
    }
}
