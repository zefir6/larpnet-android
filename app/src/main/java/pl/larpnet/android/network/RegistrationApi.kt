package pl.larpnet.android.network

import pl.larpnet.android.data.model.RegistrationChallenge
import pl.larpnet.android.data.model.RegistrationConfirmation
import pl.larpnet.android.data.model.RegistrationStarted
import retrofit2.http.Field
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * In-app account registration (friendica-larpnet's Module\Api\Mastodon\LarpnetRegistration).
 * No user token yet, so every call carries this app's OAuth client_id/client_secret instead
 * (the same pair OAuthFlow registers with POST /api/v1/apps). Errors come back as
 * `{"error": "<code>", "error_description": "<message in Polish or English>"}`, which is why
 * the language header is sent: the server picks the message language from it.
 */
interface RegistrationApi {

    @FormUrlEncoded
    @POST("api/v1/larpnet/registration/challenge")
    suspend fun challenge(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
    ): RegistrationChallenge

    /** Fields: client_id, client_secret, username, display_name, email, password, agreement,
     *  reason, platform=android, challenge, integrity_token. */
    @FormUrlEncoded
    @POST("api/v1/accounts")
    suspend fun register(
        @Header("Accept-Language") language: String,
        @FieldMap fields: Map<String, String>,
    ): RegistrationStarted

    @FormUrlEncoded
    @POST("api/v1/larpnet/registration/confirm")
    suspend fun confirm(
        @Header("Accept-Language") language: String,
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("id") id: String,
        @Field("code") code: String,
    ): RegistrationConfirmation

    @FormUrlEncoded
    @POST("api/v1/larpnet/registration/resend")
    suspend fun resend(
        @Header("Accept-Language") language: String,
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("id") id: String,
    ): RegistrationConfirmation
}
