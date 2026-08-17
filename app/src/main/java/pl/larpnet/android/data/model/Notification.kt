package pl.larpnet.android.data.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * [type] is deliberately a raw String, not an enum: Friendica documents notification
 * types beyond the standard Mastodon set (e.g. "quote", "quoted_update", "admin.*").
 * Unknown values must not crash deserialization -- map them to a generic UI treatment.
 */
@Serializable
data class Notification(
    override val id: String,
    val type: String,
    @SerialName("created_at") val createdAt: Instant,
    val account: Account,
    val status: Status? = null,
) : Identifiable
