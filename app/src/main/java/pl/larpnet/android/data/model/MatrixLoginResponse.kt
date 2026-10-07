package pl.larpnet.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response of `POST larpnet_matrix` (`larpnet_matrix_post()`). [token] is a short-lived (60s)
 * JWT, traded for a real Matrix session via `Client.customLoginWithJwt` -- never persisted,
 * re-fetched fresh on every login (see `MatrixRepository`).
 */
@Serializable
data class MatrixLoginResponse(
    @SerialName("user_id") val userId: String,
    val displayname: String = "",
    val homeserver: String,
    @SerialName("login_type") val loginType: String = "",
    val token: String,
    /** Nickname->displayname fallback for users who've never opened chat themselves (so have
     * no Matrix displayname yet) -- same list the web client's `config.contacts` uses. */
    val contacts: List<MatrixContact> = emptyList(),
    /** This deployment's Matrix push gateway URL, already including the shared secret as a
     * query param (see `larpnet_matrix_push_gateway_url()` server-side) -- null if the server
     * hasn't got push configured yet, in which case [MatrixRepository] skips pusher
     * registration entirely. Never a standalone secret: this app must never be handed
     * LARPNET_MATRIX_PUSH_SECRET on its own, only this pre-built URL. */
    @SerialName("push_gateway_url") val pushGatewayUrl: String? = null,
    /** Chat encryption mode + (in standard mode) the server-held recovery passphrase -- see
     * [MatrixEncryptionInfo]. Null from servers that predate encryption modes. */
    val encryption: MatrixEncryptionInfo? = null,
)

/**
 * `larpnet_matrix_escrow_get()`'s shape, from `POST larpnet_matrix` and
 * `POST larpnet_matrix/encryption`. See friendica-larpnet's `addon/larpnet_matrix/CLAUDE.md`
 * "Encryption modes": in [MODE_STANDARD] the server holds [passphrase] and this app unlocks
 * chat history with it silently; in [MODE_PRIVATE] (or with a null [passphrase]) the user
 * holds their own key and the manual prompts apply. [state] is [STATE_PENDING] until some
 * client confirms it applied [passphrase] to the Matrix account.
 */
@Serializable
data class MatrixEncryptionInfo(
    val mode: String,
    val state: String,
    val passphrase: String? = null,
) {
    val isStandard: Boolean get() = mode == MODE_STANDARD && !passphrase.isNullOrEmpty()
    val isPrivate: Boolean get() = mode == MODE_PRIVATE

    companion object {
        const val MODE_STANDARD = "standard"
        const val MODE_PRIVATE = "private"
        const val STATE_PENDING = "pending"
    }
}

@Serializable
data class MatrixContact(
    val nickname: String,
    val name: String,
)
