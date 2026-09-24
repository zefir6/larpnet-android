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
)

@Serializable
data class MatrixContact(
    val nickname: String,
    val name: String,
)
