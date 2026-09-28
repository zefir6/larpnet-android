package pl.larpnet.android.ui.chat

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import pl.larpnet.android.data.matrix.ChatMessage

/**
 * Groups a flat [List] of [ChatMessage] into day sections, each holding sender/time clusters --
 * the WhatsApp/Telegram/iMessage convention of collapsing consecutive same-sender messages into
 * one visual block instead of repeating an avatar/name/bubble-decoration per message. Pure data
 * transform, no Compose code, so the clustering rule is easy to reason about/tune independently
 * of `ChatThreadScreen`'s layout. Mirrors larpnet-iOS's `ChatMessageGrouping`.
 */
object ChatMessageGrouping {

    /** Consecutive messages from the same sender collapse into one cluster as long as they're
     * this close together -- matches the common WhatsApp/Telegram convention. */
    private const val CLUSTER_WINDOW_MILLIS = 60_000L

    data class Cluster(
        val id: String,
        val isOwn: Boolean,
        val senderId: String?,
        val senderDisplayName: String?,
        val messages: List<ChatMessage>,
    )

    data class DaySection(
        val id: Long,
        val dayStartMillis: Long,
        val clusters: List<Cluster>,
    )

    fun build(messages: List<ChatMessage>): List<DaySection> {
        val sections = mutableListOf<DaySection>()
        var currentDayMessages = mutableListOf<ChatMessage>()
        var currentDayStart: Long? = null

        fun flushDay() {
            val dayStart = currentDayStart ?: return
            if (currentDayMessages.isEmpty()) return
            sections.add(DaySection(id = dayStart, dayStartMillis = dayStart, clusters = clusters(currentDayMessages)))
            currentDayMessages = mutableListOf()
        }

        for (message in messages) {
            val dayStart = startOfDay(message.timestampMillis)
            if (currentDayStart == null) {
                currentDayStart = dayStart
            } else if (dayStart != currentDayStart) {
                flushDay()
                currentDayStart = dayStart
            }
            currentDayMessages.add(message)
        }
        flushDay()
        return sections
    }

    /** A stable grouping key: "self" for the local user, else the sender's mxid (falling back
     * to a constant so two consecutive messages with no reported sender id still cluster
     * together rather than each starting a new cluster). */
    private fun clusterKey(message: ChatMessage): String =
        if (message.isOwn) "self" else (message.senderId ?: "unknown-sender")

    private fun clusters(messages: List<ChatMessage>): List<Cluster> {
        val result = mutableListOf<Cluster>()
        for (message in messages) {
            val key = clusterKey(message)
            val last = result.lastOrNull()
            val lastTimestamp = last?.messages?.lastOrNull()?.timestampMillis
            if (last != null && clusterKey(last.messages[0]) == key &&
                lastTimestamp != null && message.timestampMillis - lastTimestamp <= CLUSTER_WINDOW_MILLIS
            ) {
                result[result.size - 1] = last.copy(messages = last.messages + message)
            } else {
                result.add(
                    Cluster(
                        id = message.id, isOwn = message.isOwn, senderId = message.senderId,
                        senderDisplayName = message.senderDisplayName, messages = listOf(message),
                    ),
                )
            }
        }
        return result
    }

    private fun startOfDay(timestampMillis: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestampMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    /** "Today" / "Yesterday" / a localized date, for the day-separator pill. */
    fun dayLabel(dayStartMillis: Long): String {
        val today = startOfDay(System.currentTimeMillis())
        val yesterday = today - 24 * 60 * 60 * 1000
        return when (dayStartMillis) {
            today -> "Today"
            yesterday -> "Yesterday"
            else -> {
                val sameYear = Calendar.getInstance().apply { timeInMillis = dayStartMillis }.get(Calendar.YEAR) ==
                    Calendar.getInstance().apply { timeInMillis = today }.get(Calendar.YEAR)
                val pattern = if (sameYear) "d MMMM" else "d MMMM yyyy"
                SimpleDateFormat(pattern, Locale.getDefault()).format(dayStartMillis)
            }
        }
    }
}
