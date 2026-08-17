package pl.larpnet.android.network

import kotlinx.serialization.SerializationException
import okio.IOException
import retrofit2.HttpException

/** Analogue of the sibling macOS app's src-tauri/src/friendica/error.rs error enum. */
sealed class NetworkError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Network(cause: Throwable) : NetworkError("Network error: ${cause.message}", cause)
    class Http(val status: Int, val body: String) : NetworkError("HTTP $status: $body")
    object Auth : NetworkError("Authentication failed")
    class Parse(cause: Throwable) : NetworkError("Failed to parse response: ${cause.message}", cause)
    object NotLoggedIn : NetworkError("Not logged in")
    class InvalidUrl(url: String) : NetworkError("Invalid instance URL: $url")
}

/**
 * Wraps a suspend API call, mapping transport/HTTP/parse failures onto [NetworkError].
 * 401/403 map to [NetworkError.Auth] specifically so callers (repositories) can forward
 * it to AuthRepository.forceLogoutEvents without inspecting status codes themselves.
 */
suspend fun <T> safeApiCall(block: suspend () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (e: HttpException) {
        val code = e.code()
        if (code == 401 || code == 403) {
            Result.failure(NetworkError.Auth)
        } else {
            val body = e.response()?.errorBody()?.string().orEmpty()
            Result.failure(NetworkError.Http(code, body))
        }
    } catch (e: SerializationException) {
        Result.failure(NetworkError.Parse(e))
    } catch (e: IOException) {
        Result.failure(NetworkError.Network(e))
    }
}
