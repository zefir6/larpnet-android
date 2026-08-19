package pl.larpnet.android.ui.login

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pl.larpnet.android.BuildConfig
import pl.larpnet.android.data.auth.TokenStore
import pl.larpnet.android.data.repository.AuthRepository

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object AwaitingBrowser : LoginUiState
    data object ExchangingToken : LoginUiState
    data class Error(val message: String) : LoginUiState
    data object LoggedIn : LoginUiState
}

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val tokenStore: TokenStore,
) : ViewModel() {

    /**
     * No server-address field on this screen anymore -- LARPnet is a single-instance app, so
     * login proceeds straight to the browser. [TokenStore.instanceBaseUrl] already survives
     * [TokenStore.clear] (logout only clears the token/app registration, not the instance), so
     * reusing it here doubles it as "which instance to log back into" -- Settings' advanced
     * "server address" field writes directly to this same pref for anyone who needs a different
     * instance (e.g. a staging server), rather than adding a second, separate stored value.
     */
    private val instanceUrl: String
        get() = tokenStore.instanceBaseUrl?.trim()?.takeIf { it.isNotBlank() } ?: BuildConfig.DEFAULT_INSTANCE

    var uiState by mutableStateOf<LoginUiState>(LoginUiState.Idle)
        private set

    // CSRF guard: the /oauth/authorize `state` param we sent, checked against the redirect's.
    private var pendingState: String? = null
    private var pendingBaseUrl: String? = null

    /** Registers the app (if needed) and opens the server's login/consent page in a Custom Tab. */
    fun startLogin(context: Context) {
        val baseUrl = instanceUrl
        uiState = LoginUiState.AwaitingBrowser
        viewModelScope.launch {
            authRepository.beginLogin(baseUrl).fold(
                onSuccess = { registration ->
                    val state = authRepository.newState()
                    pendingState = state
                    pendingBaseUrl = baseUrl
                    val authorizeUri = authRepository.authorizeUri(baseUrl, registration.clientId, state)
                    authRepository.launchAuthorization(context, authorizeUri)
                },
                onFailure = { error ->
                    uiState = LoginUiState.Error(error.message ?: "registration_failed")
                },
            )
        }
    }

    /**
     * Custom Tabs' simple `launchUrl` API gives no callback when the user backs out of the
     * browser without completing the OAuth flow (that needs a session-bound
     * CustomTabsServiceConnection, more machinery than a one-shot login screen needs). As a
     * cheap approximation, LoginScreen calls this when the screen resumes; if we're still
     * waiting on a browser round-trip at that point, the user almost certainly backed out, so
     * drop back to Idle instead of leaving the button permanently disabled.
     */
    fun onScreenResumed() {
        if (uiState is LoginUiState.AwaitingBrowser) {
            uiState = LoginUiState.Idle
        }
    }

    /** Called with the redirect Uri forwarded from OAuthRedirectActivity via AppContainer.oauthCallbackEvents. */
    fun handleCallback(uri: Uri) {
        val code = authRepository.extractCode(uri)
        val state = authRepository.extractState(uri)
        val baseUrl = pendingBaseUrl

        if (code == null || baseUrl == null || state == null || state != pendingState) {
            uiState = LoginUiState.Error("invalid_callback")
            return
        }

        uiState = LoginUiState.ExchangingToken
        viewModelScope.launch {
            authRepository.completeLogin(baseUrl, code).fold(
                onSuccess = { uiState = LoginUiState.LoggedIn },
                onFailure = { error -> uiState = LoginUiState.Error(error.message ?: "token_exchange_failed") },
            )
        }
    }
}
