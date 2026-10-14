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
    val tags: List<String> = emptyList(),
) {
    /** Room/event of a chat message pushed by the server's web pusher (tagged [CHAT_TAG], see
     * friendica-larpnet's `larpnet_matrix_push_deliver_web()`), parsed from its click URL --
     * null for a classic Friendica notification. */
    val chatTarget: Pair<String, String>?
        get() {
            if (CHAT_TAG !in tags) return null
            val uri = click?.let { runCatching { android.net.Uri.parse(it) }.getOrNull() } ?: return null
            val room = uri.getQueryParameter("room")?.takeIf { it.isNotEmpty() } ?: return null
            val event = uri.getQueryParameter("event")?.takeIf { it.isNotEmpty() } ?: return null
            return room to event
        }

    companion object {
        const val CHAT_TAG = "larpnet-chat"
    }
}
