package pl.larpnet.android.push

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * What each room's chat notification currently shows, kept by the app itself instead of read
 * back from the posted notification. The old approach (extract the showing MessagingStyle,
 * append) couldn't tell a re-delivered push (Synapse retry, FCM redelivery) from a new message,
 * and nothing ever pruned it, so a room's notification only ever grew. Here every line is keyed
 * by its event id, and a room is dropped as soon as it's read or dismissed (see
 * [PushNotifications.cancelChat]/[PushNotifications.reconcileChat]).
 *
 * Persisted in SharedPreferences, since a chat push usually lands in a freshly started process.
 * Not thread-safe on its own -- [PushNotifications] only calls it under its own lock, together
 * with posting/cancelling the notification it describes.
 */
internal class ChatNotificationStore(context: Context) {

    @Serializable
    data class Line(
        val eventId: String,
        val senderName: String,
        val body: String,
        val timestampMillis: Long,
        /** Generic "new message" shown when the event couldn't be decrypted in time -- at most
         * one per room, replaced by the real line if the same event is pushed again later. */
        val placeholder: Boolean = false,
    )

    @Serializable
    data class RoomEntry(
        /** Group room name; null for a 1:1 DM. */
        val roomName: String? = null,
        /** Last [MAX_LINES] lines, oldest first -- what the MessagingStyle shows. */
        val lines: List<Line> = emptyList(),
        /** Event ids already shown since the room was last cleared, beyond [lines] too, so a
         * late re-delivery of an evicted line still counts as a duplicate. */
        val seenEventIds: List<String> = emptyList(),
        /** New messages since the room was last cleared (not capped like [lines]). */
        val count: Int = 0,
        /** When this entry last changed -- see [PushNotifications.reconcileChat]'s grace period. */
        val updatedAtMillis: Long = 0,
    )

    enum class AddResult { NEW, REPLACED_PLACEHOLDER, DUPLICATE }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), RoomEntry.serializer())
    private val rooms: MutableMap<String, RoomEntry> = load()

    fun get(roomId: String): RoomEntry? = rooms[roomId]

    fun all(): Map<String, RoomEntry> = rooms.toMap()

    fun add(roomId: String, roomName: String?, line: Line, nowMillis: Long): AddResult {
        val entry = rooms[roomId] ?: RoomEntry()
        val existing = entry.lines.indexOfFirst { it.eventId == line.eventId }
        val result = when {
            existing >= 0 && entry.lines[existing].placeholder && !line.placeholder -> AddResult.REPLACED_PLACEHOLDER
            existing >= 0 || line.eventId in entry.seenEventIds -> return AddResult.DUPLICATE
            else -> AddResult.NEW
        }
        val lines = if (result == AddResult.REPLACED_PLACEHOLDER) {
            entry.lines.toMutableList().also { it[existing] = line }
        } else {
            val base = if (line.placeholder) entry.lines.filterNot { it.placeholder } else entry.lines
            (base + line).takeLast(MAX_LINES)
        }
        rooms[roomId] = entry.copy(
            roomName = roomName ?: entry.roomName,
            lines = lines,
            seenEventIds = (entry.seenEventIds + line.eventId).distinct().takeLast(MAX_SEEN),
            count = if (result == AddResult.NEW) entry.count + 1 else entry.count,
            updatedAtMillis = nowMillis,
        )
        save()
        return result
    }

    /** Returns true if [roomId] had anything stored. */
    fun remove(roomId: String): Boolean {
        val removed = rooms.remove(roomId) != null
        if (removed) save()
        return removed
    }

    private fun load(): MutableMap<String, RoomEntry> =
        prefs.getString(KEY_ROOMS, null)
            ?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
            ?.toMutableMap()
            ?: mutableMapOf()

    private fun save() {
        prefs.edit { putString(KEY_ROOMS, json.encodeToString(serializer, rooms)) }
    }

    private companion object {
        const val PREFS_NAME = "chat_notifications"
        const val KEY_ROOMS = "rooms"
        const val MAX_LINES = 7
        const val MAX_SEEN = 50
    }
}
