package pl.larpnet.android.network

import pl.larpnet.android.data.model.AppRegistration
import pl.larpnet.android.data.model.TokenResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * Unauthenticated endpoints: OAuth client registration and token exchange. Built on the
 * default `read write follow push` scope -- must be sent identically here, in the
 * /oauth/authorize URL (see data/auth/OAuthFlow.kt), and here again at token exchange, since
 * Module\Api\Mastodon\Apps.php derives read/write/follow/push flags from this exact string and
 * write endpoints 401 unless "write" was granted at registration. "push" is what
 * POST /larpnet_fcm (PushRepository, FCM token registration) requires -- see its
 * BaseApi::getCurrentApplication()['push'] check server-side.
 */
interface AuthApi {

    @FormUrlEncoded
    @POST("api/v1/apps")
    suspend fun registerApp(
        @Field("client_name") clientName: String,
        @Field("redirect_uris") redirectUri: String,
        @Field("scopes") scopes: String = OAUTH_SCOPES,
        @Field("website") website: String = "https://larpnet.pl",
    ): AppRegistration

    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun exchangeToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("redirect_uri") redirectUri: String,
        @Field("code") code: String,
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("scope") scope: String = OAUTH_SCOPES,
    ): TokenResponse

    @FormUrlEncoded
    @POST("oauth/revoke")
    suspend fun revokeToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("token") token: String,
    )

    companion object {
        const val OAUTH_SCOPES = "read write follow push"
    }
}
