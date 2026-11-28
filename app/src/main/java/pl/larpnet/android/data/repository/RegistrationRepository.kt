package pl.larpnet.android.data.repository

import android.util.Log
import pl.larpnet.android.data.auth.IntegrityProvider
import pl.larpnet.android.data.auth.OAuthFlow
import pl.larpnet.android.data.auth.TokenStore
import pl.larpnet.android.data.model.RegistrationError
import pl.larpnet.android.network.InstanceUrl
import pl.larpnet.android.network.RegistrationApi
import pl.larpnet.android.network.friendicaJson
import retrofit2.HttpException
import java.io.IOException
import java.util.Locale

/** What the person typed on the registration form. */
data class RegistrationForm(
    val displayName: String,
    val username: String,
    val email: String,
    val password: String,
    val reason: String,
    val agreed: Boolean,
)

sealed interface RegisterResult {
    /** Account created, unconfirmed; the code is on its way to [email]. */
    data class Started(val id: String, val email: String, val approvalRequired: Boolean) : RegisterResult

    /** [useWebsite]: this device can't prove the app is genuine -- offer the web form instead. */
    data class Failed(val message: String?, val useWebsite: Boolean = false) : RegisterResult
}

sealed interface ConfirmResult {
    /** Confirmed, and the server handed us a token: the person is logged in now. */
    data object LoggedIn : ConfirmResult

    /** Confirmed, but an admin has to approve the account before it can log in. */
    data object ApprovalPending : ConfirmResult

    /** Confirmed without a token for this app (rare): log in the normal way. */
    data object ConfirmedLogIn : ConfirmResult

    /** [expired]: the registration is gone (48 h passed) -- start over. */
    data class Failed(val message: String?, val expired: Boolean = false) : ConfirmResult
}

/**
 * In-app registration against friendica-larpnet's registration API (see [RegistrationApi]).
 * Proof that the request comes from this genuine app is a Play Integrity token over the
 * server's single-use challenge ([IntegrityProvider]).
 */
class RegistrationRepository(
    private val tokenStore: TokenStore,
    private val oAuthFlow: OAuthFlow,
    private val apiFactory: (baseUrl: String) -> RegistrationApi,
    private val integrity: IntegrityProvider,
) {
    private val language: String get() = Locale.getDefault().toLanguageTag()

    private companion object {
        const val TAG = "Registration"
    }

    suspend fun register(baseUrl: String, form: RegistrationForm): RegisterResult {
        val app = try {
            oAuthFlow.ensureAppRegistered(baseUrl)
        } catch (e: IOException) {
            return RegisterResult.Failed(null)
        } catch (e: HttpException) {
            return RegisterResult.Failed(errorOf(e)?.description)
        }
        val api = apiFactory(InstanceUrl.normalize(baseUrl))

        return try {
            val challenge = api.challenge(app.clientId, app.clientSecret).challenge
            // No Play services, or Google refused: send no token and let the server decide
            // (it refuses by default -- `attestation_failed`, handled below -- but test servers
            // can let such registrations through into the approval queue).
            val token = try {
                integrity.token(challenge)
            } catch (e: Exception) {
                Log.w(TAG, "Play Integrity unavailable: ${e.message}")
                ""
            }
            val started = api.register(
                language,
                mapOf(
                    "client_id" to app.clientId,
                    "client_secret" to app.clientSecret,
                    "display_name" to form.displayName.trim(),
                    "username" to form.username.trim(),
                    "email" to form.email.trim(),
                    "password" to form.password,
                    "reason" to form.reason.trim(),
                    "agreement" to if (form.agreed) "true" else "false",
                    "locale" to language,
                    "platform" to "android",
                    "challenge" to challenge,
                    "integrity_token" to token,
                ),
            )
            RegisterResult.Started(started.id, started.email.ifBlank { form.email.trim() }, started.approvalRequired)
        } catch (e: HttpException) {
            val error = errorOf(e)
            RegisterResult.Failed(error?.description, useWebsite = error?.error == "attestation_failed")
        } catch (e: IOException) {
            RegisterResult.Failed(null)
        }
    }

    suspend fun confirm(baseUrl: String, id: String, code: String): ConfirmResult {
        val clientId = tokenStore.clientId ?: return ConfirmResult.Failed(null, expired = true)
        val clientSecret = tokenStore.clientSecret ?: return ConfirmResult.Failed(null, expired = true)
        return try {
            val result = apiFactory(InstanceUrl.normalize(baseUrl)).confirm(language, clientId, clientSecret, id, code.filter(Char::isDigit))
            when {
                result.status == "approval_pending" -> ConfirmResult.ApprovalPending
                !result.accessToken.isNullOrBlank() -> {
                    tokenStore.accessToken = result.accessToken
                    ConfirmResult.LoggedIn
                }
                else -> ConfirmResult.ConfirmedLogIn
            }
        } catch (e: HttpException) {
            ConfirmResult.Failed(errorOf(e)?.description, expired = e.code() == 410)
        } catch (e: IOException) {
            ConfirmResult.Failed(null)
        }
    }

    /** null on success, otherwise the message to show (blank = generic network error). */
    suspend fun resend(baseUrl: String, id: String): String? {
        val clientId = tokenStore.clientId ?: return ""
        val clientSecret = tokenStore.clientSecret ?: return ""
        return try {
            apiFactory(InstanceUrl.normalize(baseUrl)).resend(language, clientId, clientSecret, id)
            null
        } catch (e: HttpException) {
            errorOf(e)?.description ?: ""
        } catch (e: IOException) {
            ""
        }
    }

    private fun errorOf(e: HttpException): RegistrationError? =
        runCatching {
            friendicaJson.decodeFromString(RegistrationError.serializer(), e.response()?.errorBody()?.string().orEmpty())
        }.getOrNull()?.takeIf { it.description.isNotBlank() || it.error.isNotBlank() }
}
