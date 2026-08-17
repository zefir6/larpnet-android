package pl.larpnet.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Account(
    val id: String,
    val username: String,
    val acct: String,
    @SerialName("display_name") val displayName: String = "",
    val avatar: String = "",
    val url: String = "",
    val note: String = "",
    val locked: Boolean = false,
    val bot: Boolean = false,
    val discoverable: Boolean = true,
    @SerialName("followers_count") val followersCount: Long = 0,
    @SerialName("following_count") val followingCount: Long = 0,
    @SerialName("statuses_count") val statusesCount: Long = 0,
    @SerialName("header") val header: String = "",
)

@Serializable
data class Relationship(
    val id: String,
    val following: Boolean = false,
    @SerialName("followed_by") val followedBy: Boolean = false,
    val blocking: Boolean = false,
    val muting: Boolean = false,
    val requested: Boolean = false,
)
