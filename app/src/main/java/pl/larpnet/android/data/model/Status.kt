package pl.larpnet.android.data.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MediaAttachment(
    val id: String,
    @SerialName("type") val mediaType: String = "unknown",
    val url: String = "",
    @SerialName("preview_url") val previewUrl: String? = null,
    val description: String? = null,
)

@Serializable
data class PollOption(
    val title: String,
    @SerialName("votes_count") val votesCount: Long? = null,
)

/**
 * Read-only: poll voting is unimplemented server-side (Module\Api\Mastodon\Unimplemented),
 * so the UI only ever displays this, never submits votes.
 */
@Serializable
data class Poll(
    val id: String,
    @SerialName("expires_at") val expiresAt: Instant? = null,
    val expired: Boolean = false,
    val multiple: Boolean = false,
    @SerialName("votes_count") val votesCount: Long = 0,
    val options: List<PollOption> = emptyList(),
    val voted: Boolean = false,
)

/**
 * Mirrors Friendica's Object\Api\Mastodon\Status shape. Notable server-specific quirks
 * (see plan doc "Verified API facts"):
 *  - [visibility] is only ever "public" | "private" | "unlisted" -- "direct" is never
 *    emitted, even for DMs (those are surfaced via /api/v1/conversations instead).
 *  - [spoilerText] is frequently just the post's title/subject, not a strict CW flag --
 *    UI should only collapse content behind it when [sensitive] is true.
 *  - [inReplyToAccountId] is the *thread root's* author, not the direct parent's --
 *    don't use it for "replying to @X" labels; resolve the parent via ThreadBuilder instead.
 */
@Serializable
data class Status(
    override val id: String,
    @SerialName("created_at") val createdAt: Instant,
    val content: String = "",
    @SerialName("spoiler_text") val spoilerText: String = "",
    val sensitive: Boolean = false,
    val visibility: String = "public",
    val account: Account,
    val reblog: Status? = null,
    @SerialName("in_reply_to_id") val inReplyToId: String? = null,
    @SerialName("in_reply_to_account_id") val inReplyToAccountId: String? = null,
    val url: String? = null,
    @SerialName("media_attachments") val mediaAttachments: List<MediaAttachment> = emptyList(),
    val poll: Poll? = null,
    val favourited: Boolean = false,
    val reblogged: Boolean = false,
    val bookmarked: Boolean = false,
    @SerialName("favourites_count") val favouritesCount: Long = 0,
    @SerialName("reblogs_count") val reblogsCount: Long = 0,
    @SerialName("replies_count") val repliesCount: Long = 0,
    val language: String? = null,
) : Identifiable

@Serializable
data class StatusContext(
    val ancestors: List<Status> = emptyList(),
    val descendants: List<Status> = emptyList(),
)
