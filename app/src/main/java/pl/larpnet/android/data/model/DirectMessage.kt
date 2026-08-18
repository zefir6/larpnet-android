package pl.larpnet.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A single message inside a 1:1 conversation, from the Twitter-compat
 * `/api/direct_messages/all.json` and `/api/direct_messages/new.json` endpoints -- the only
 * write path into Friendica's legacy `mail` table, which is what `/api/v1/conversations` (and
 * the web UI's own Messages) actually read from. `POST /api/v1/statuses?visibility=direct` is
 * a completely separate, disconnected system server-side (see ConversationRepository doc).
 *
 * `created_at` is intentionally left as a raw string: this endpoint formats dates as
 * `DateTimeFormat::API` ("EEE MMM d HH:mm:ss Z yyyy", classic Twitter format), not the ISO8601
 * the Mastodon-API layer uses elsewhere in this app -- see [parseTwitterDate].
 */
@Serializable
data class DirectMessage(
    /** Absent (empty string) on the `{"error": N}` failure shape -- see [error]. */
    override val id: String = "",
    @SerialName("sender_id") val senderId: String = "",
    @SerialName("recipient_id") val recipientId: String = "",
    val text: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("sender_screen_name") val senderScreenName: String = "",
    @SerialName("recipient_screen_name") val recipientScreenName: String = "",
    @SerialName("friendica_seen") val seen: Boolean = false,
    /** Set instead of the fields above when the send failed (e.g. recipient not resolvable). */
    val error: Int? = null,
) : Identifiable

private val twitterDateFormatter = java.time.format.DateTimeFormatter
    .ofPattern("EEE MMM d HH:mm:ss Z yyyy", java.util.Locale.US)

/** Parses [DirectMessage.createdAt]; returns null (rather than throwing) on any unexpected format. */
fun parseTwitterDate(raw: String): kotlinx.datetime.Instant? =
    runCatching {
        val offsetDateTime = java.time.OffsetDateTime.parse(raw, twitterDateFormatter)
        kotlinx.datetime.Instant.fromEpochMilliseconds(offsetDateTime.toInstant().toEpochMilli())
    }.getOrNull()
