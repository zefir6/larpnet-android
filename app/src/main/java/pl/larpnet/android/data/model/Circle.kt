package pl.larpnet.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One of the user's Friendica circles ("groups"), from the Twitter-compat
 * `GET /api/friendica/circle_show.json` -- the Mastodon-API layer has no equivalent. The
 * response is a bare JSON array, not wrapped in an object.
 */
@Serializable
data class Circle(
    val name: String = "",
    val gid: Int = 0,
)

/** `GET /api/followers/list.json` response -- Twitter-compat cursor-paginated user listing. */
@Serializable
data class FollowerListResponse(
    val users: List<FollowerEntry> = emptyList(),
)

/**
 * One follower, from [FollowerListResponse]. Deliberately not reusing [Account]: this listing
 * is Twitter-shaped and its [cid] is the only source in this app for a value
 * `contact_allow[]`/`circle_allow[]` (see [pl.larpnet.android.network.FriendicaApi.postStatusWithAudience])
 * will actually accept -- `Account.id` (used everywhere else) is a different id space and
 * silently resolves to nothing there.
 */
@Serializable
data class FollowerEntry(
    val cid: Int = 0,
    val name: String = "",
    @SerialName("screen_name") val screenName: String = "",
    @SerialName("profile_image_url") val profileImageUrl: String = "",
)

/** Just enough of `/api/statuses/update.json`'s Twitter-shaped response to re-fetch the post via the Mastodon API. */
@Serializable
data class LegacyStatusRef(
    @SerialName("id_str") val idStr: String = "",
)
