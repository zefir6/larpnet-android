package pl.larpnet.android.data.auth

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import pl.larpnet.android.BuildConfig
import pl.larpnet.android.data.model.AppRegistration
import pl.larpnet.android.data.model.TokenResponse
import pl.larpnet.android.network.AuthApi
import pl.larpnet.android.network.InstanceUrl
import java.util.UUID

/**
 * Hand-rolled OAuth2 authorization-code flow (no AppAuth library -- the server has no
 * OIDC discovery document and no PKCE support, see src/Module/OAuth/{Authorize,Token}.php,
 * so AppAuth's assumptions don't fit and a native implementation is simpler and more correct).
 *
 * Flow: [ensureAppRegistered] once per instance -> [buildAuthorizeUri] opened in a Custom Tab
 * (the server's /oauth/authorize is a browser-rendered login/consent page, not a JSON
 * endpoint) -> the redirect lands on OAuthRedirectActivity, which forwards the callback Uri
 * here via [exchangeCode].
 */
class OAuthFlow(
    private val tokenStore: TokenStore,
    private val authApiFactory: (baseUrl: String) -> AuthApi,
) {

    /** Registers this app with the instance if not already cached for it, returning the client credentials. */
    suspend fun ensureAppRegistered(baseUrl: String): AppRegistration {
        val normalized = InstanceUrl.normalize(baseUrl)
        val cachedId = tokenStore.clientId
        val cachedSecret = tokenStore.clientSecret
        if (cachedId != null && cachedSecret != null && tokenStore.instanceBaseUrl == normalized) {
            return AppRegistration(id = "", clientId = cachedId, clientSecret = cachedSecret)
        }

        val registration = authApiFactory(normalized).registerApp(
            clientName = "Larpnet Android",
            redirectUri = BuildConfig.OAUTH_REDIRECT_URI,
        )
        tokenStore.instanceBaseUrl = normalized
        tokenStore.clientId = registration.clientId
        tokenStore.clientSecret = registration.clientSecret
        return registration
    }

    /** A fresh per-attempt opaque value, checked against the redirect's `state` param to reject stray callbacks. */
    fun newState(): String = UUID.randomUUID().toString()

    fun buildAuthorizeUri(baseUrl: String, clientId: String, state: String): Uri {
        return Uri.parse(InstanceUrl.normalize(baseUrl)).buildUpon()
            .appendEncodedPath("oauth/authorize")
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", BuildConfig.OAUTH_REDIRECT_URI)
            .appendQueryParameter("scope", AuthApi.OAUTH_SCOPES)
            .appendQueryParameter("state", state)
            .build()
    }

    fun launchAuthorization(context: Context, authorizeUri: Uri) {
        CustomTabsIntent.Builder().build().launchUrl(context, authorizeUri)
    }

    /** Parses the code out of the OAuthRedirectActivity callback Uri, returning null if it's not a valid callback. */
    fun extractCode(callbackUri: Uri): String? = callbackUri.getQueryParameter("code")

    fun extractState(callbackUri: Uri): String? = callbackUri.getQueryParameter("state")

    /** Exchanges an authorization code for an access token and persists it to [tokenStore]. */
    suspend fun exchangeCode(baseUrl: String, code: String): TokenResponse {
        val normalized = InstanceUrl.normalize(baseUrl)
        val clientId = tokenStore.clientId ?: error("App not registered for this instance yet")
        val clientSecret = tokenStore.clientSecret ?: error("App not registered for this instance yet")

        val token = authApiFactory(normalized).exchangeToken(
            clientId = clientId,
            clientSecret = clientSecret,
            redirectUri = BuildConfig.OAUTH_REDIRECT_URI,
            code = code,
        )
        tokenStore.accessToken = token.accessToken
        return token
    }
}
