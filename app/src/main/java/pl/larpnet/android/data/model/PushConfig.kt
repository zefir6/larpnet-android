package pl.larpnet.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Response of `GET /api/v1/larpnet_push_config` -- what the app needs to subscribe directly to the ntfy relay. */
@Serializable
data class PushConfig(
    val enabled: Boolean = false,
    @SerialName("ntfy_url") val ntfyUrl: String = "",
    @SerialName("ntfy_topic") val ntfyTopic: String = "",
    @SerialName("ntfy_token") val ntfyToken: String = "",
)
