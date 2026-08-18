package pl.larpnet.android.push

import kotlinx.serialization.Serializable

/** One line of ntfy's `/json` streaming endpoint. See https://docs.ntfy.sh/subscribe/api/#json-stream */
@Serializable
data class NtfyMessage(
    val id: String = "",
    val event: String = "",
    val topic: String = "",
    val title: String? = null,
    val message: String? = null,
    val click: String? = null,
    val icon: String? = null,
)
