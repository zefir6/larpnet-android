package pl.larpnet.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Friendica identifies an album by its name string, not a numeric id -- see FriendicaApi.photoAlbums. */
@Serializable
data class FriendicaPhotoAlbum(val name: String, val count: Int = 0)

@Serializable
data class FriendicaPhoto(
    @SerialName("resource-id") val id: String,
    val album: String = "",
    val filename: String = "",
    val type: String = "",
    val thumb: String = "",
)
