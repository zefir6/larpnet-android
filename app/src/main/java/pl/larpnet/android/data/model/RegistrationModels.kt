package pl.larpnet.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** POST /api/v1/larpnet/registration/challenge -- single use, valid 10 minutes. */
@Serializable
data class RegistrationChallenge(
    val challenge: String,
    @SerialName("expires_in") val expiresIn: Int = 600,
)

/** POST /api/v1/accounts -- the account exists, unconfirmed, until the emailed code is entered. */
@Serializable
data class RegistrationStarted(
    val id: String,
    val email: String = "",
    @SerialName("approval_required") val approvalRequired: Boolean = false,
)

/**
 * POST /api/v1/larpnet/registration/confirm (and /resend, which only fills [status] = "sent").
 * `confirmed` carries an access token for this app when no admin approval is needed.
 */
@Serializable
data class RegistrationConfirmation(
    val status: String,
    @SerialName("access_token") val accessToken: String? = null,
)

/** The server's error body for every registration call. */
@Serializable
data class RegistrationError(
    val error: String = "",
    @SerialName("error_description") val description: String = "",
    val remaining: Int? = null,
)
