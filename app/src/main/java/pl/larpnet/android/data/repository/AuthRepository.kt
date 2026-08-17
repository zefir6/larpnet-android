package pl.larpnet.android.data.repository

import android.content.Context
import android.net.Uri
import pl.larpnet.android.data.auth.OAuthFlow
import pl.larpnet.android.data.auth.TokenStore
import pl.larpnet.android.data.model.Account
import pl.larpnet.android.data.model.AppRegistration
import pl.larpnet.android.network.FriendicaApi
import pl.larpnet.android.network.safeApiCall

/**
 * Orchestrates the OAuth2 login flow (delegating the actual protocol to [OAuthFlow]) and
 * exposes the logged-in state derived from [TokenStore].
 */
class AuthRepository(
    private val tokenStore: TokenStore,
    private val oAuthFlow: OAuthFlow,
    private val apiProvider: () -> FriendicaApi,
) {
    val isLoggedIn: Boolean get() = tokenStore.isLoggedIn

    /** Registers the app with [baseUrl] if needed, returning the client id to build the authorize URL with. */
    suspend fun beginLogin(baseUrl: String): Result<AppRegistration> = safeApiCall {
        oAuthFlow.ensureAppRegistered(baseUrl)
    }

    fun newState(): String = oAuthFlow.newState()

    fun authorizeUri(baseUrl: String, clientId: String, state: String): Uri =
        oAuthFlow.buildAuthorizeUri(baseUrl, clientId, state)

    fun launchAuthorization(context: Context, uri: Uri) = oAuthFlow.launchAuthorization(context, uri)

    fun extractCode(callbackUri: Uri): String? = oAuthFlow.extractCode(callbackUri)

    fun extractState(callbackUri: Uri): String? = oAuthFlow.extractState(callbackUri)

    /** Exchanges the code for a token and fetches the now-authenticated account. */
    suspend fun completeLogin(baseUrl: String, code: String): Result<Account> = safeApiCall {
        oAuthFlow.exchangeCode(baseUrl, code)
        apiProvider().verifyCredentials()
    }

    suspend fun currentAccount(): Result<Account> = safeApiCall { apiProvider().verifyCredentials() }

    /** Clears the local token; there's no server-side revoke call here since that requires the
     *  (now possibly-invalid) client secret round trip -- a plain local clear is enough to force
     *  the next launch back to the login screen. */
    fun logout() = tokenStore.clear()
}
