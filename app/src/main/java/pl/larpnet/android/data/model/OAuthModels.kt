package pl.larpnet.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Response of POST /api/v1/apps -- registering this client with the instance, once per instance. */
@Serializable
data class AppRegistration(
    val id: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String,
)

/**
 * Response of POST /oauth/token. Confirmed (src/Module/OAuth/Token.php,
 * src/Object/Api/Mastodon/Token.php) that this server never emits a refresh_token
 * or expires_in -- tokens don't expire, the only lifecycle event is revocation,
 * surfaced to the client as a 401/403. Do not add refresh logic here.
 */
@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "Bearer",
    val scope: String = "read",
)
