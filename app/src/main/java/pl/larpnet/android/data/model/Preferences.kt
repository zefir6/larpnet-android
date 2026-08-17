package pl.larpnet.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET-only on this server (see [pl.larpnet.android.network.FriendicaApi.preferences]) -- no write route exists for any of these fields. */
@Serializable
data class Preferences(
    @SerialName("posting:default:language") val postingDefaultLanguage: String = "",
)
