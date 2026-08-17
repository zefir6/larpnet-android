package pl.larpnet.android.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.Interceptor
import okhttp3.Response
import pl.larpnet.android.data.auth.TokenStore

/**
 * Attaches `Authorization: Bearer <token>` to every request. Never retries on 401/403 --
 * instead it emits a one-shot event on [forceLogoutEvents] that MainActivity collects to
 * clear the stored token and navigate back to the login screen. This is the event-driven
 * equivalent of the sibling macOS app's error.rs mapping 401/403 to a single Auth variant;
 * Compose needs a navigation side-effect here rather than a thrown error per call site.
 */
class AuthInterceptor(private val tokenStore: TokenStore) : Interceptor {

    private val _forceLogoutEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val forceLogoutEvents: SharedFlow<Unit> = _forceLogoutEvents.asSharedFlow()

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenStore.accessToken
        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }

        val response = chain.proceed(request)
        if (response.code == 401 || response.code == 403) {
            _forceLogoutEvents.tryEmit(Unit)
        }
        return response
    }
}
