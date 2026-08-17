package pl.larpnet.android.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import pl.larpnet.android.data.model.Account
import pl.larpnet.android.data.model.Conversation
import pl.larpnet.android.data.model.Instance
import pl.larpnet.android.data.model.MediaAttachment
import pl.larpnet.android.data.model.Notification
import pl.larpnet.android.data.model.Preferences
import pl.larpnet.android.data.model.Relationship
import pl.larpnet.android.data.model.Status
import pl.larpnet.android.data.model.StatusContext
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Authenticated Mastodon-compatible endpoints (Bearer token attached by AuthInterceptor).
 * List-returning calls return `Response<List<T>>`, not a bare `List<T>`, specifically so
 * callers can reach the `Link` pagination header via [parseLinkHeader] -- Retrofit hides
 * response headers from the caller once you unwrap to the bare body type.
 *
 * Endpoint coverage matches static/routes.config.php in friendica-larpnet; routes that map
 * to Module\Api\Mastodon\Unimplemented server-side (streaming, poll voting, filters,
 * featured tags, admin, domain blocks) are intentionally not declared here -- see the plan
 * doc's "v1 exclusions" section.
 */
interface FriendicaApi {

    // -- Accounts --------------------------------------------------------

    @GET("api/v1/accounts/verify_credentials")
    suspend fun verifyCredentials(): Account

    /** GET-only route (static/routes.config.php) -- no write endpoint exists for any preferences field. */
    @GET("api/v1/preferences")
    suspend fun preferences(): Preferences

    @PATCH("api/v1/accounts/update_credentials")
    @FormUrlEncoded
    suspend fun updateCredentials(
        @Field("display_name") displayName: String?,
        @Field("note") note: String?,
        @Field("locked") locked: Boolean?,
        @Field("discoverable") discoverable: Boolean?,
        @Field("bot") bot: Boolean?,
    ): Account

    @GET("api/v1/accounts/{id}")
    suspend fun getAccount(@Path("id") id: String): Account

    @GET("api/v1/accounts/search")
    suspend fun searchAccounts(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20,
        @Query("resolve") resolve: Boolean = false,
    ): List<Account>

    /** No Link-header pagination here (Directory.php just does `jsonExit`) -- callers page via [offset]. */
    @GET("api/v1/directory")
    suspend fun directory(
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 40,
        @Query("order") order: String = "active",
        @Query("local") local: Boolean = true,
    ): List<Account>

    @GET("api/v1/accounts/{id}/statuses")
    suspend fun getAccountStatuses(
        @Path("id") id: String,
        @Query("max_id") maxId: String?,
    ): Response<List<Status>>

    @POST("api/v1/accounts/{id}/follow")
    suspend fun follow(@Path("id") id: String): Relationship

    @POST("api/v1/accounts/{id}/unfollow")
    suspend fun unfollow(@Path("id") id: String): Relationship

    @GET("api/v1/accounts/relationships")
    suspend fun relationships(@Query("id[]") ids: List<String>): List<Relationship>

    // -- Timelines ---------------------------------------------------------

    @GET("api/v1/timelines/home")
    suspend fun homeTimeline(
        @Query("max_id") maxId: String? = null,
        @Query("since_id") sinceId: String? = null,
        @Query("limit") limit: Int = 40,
    ): Response<List<Status>>

    @GET("api/v1/timelines/public")
    suspend fun publicTimeline(
        @Query("max_id") maxId: String? = null,
        @Query("since_id") sinceId: String? = null,
        @Query("limit") limit: Int = 40,
        @Query("local") local: Boolean? = null,
    ): Response<List<Status>>

    @GET("api/v1/timelines/tag/{hashtag}")
    suspend fun tagTimeline(
        @Path("hashtag") hashtag: String,
        @Query("max_id") maxId: String? = null,
    ): Response<List<Status>>

    // -- Statuses ------------------------------------------------------------

    @GET("api/v1/statuses/{id}")
    suspend fun getStatus(@Path("id") id: String): Status

    @GET("api/v1/statuses/{id}/context")
    suspend fun getContext(@Path("id") id: String): StatusContext

    @FormUrlEncoded
    @POST("api/v1/statuses")
    suspend fun postStatus(
        @Field("status") status: String,
        @Field("in_reply_to_id") inReplyToId: String? = null,
        @Field("visibility") visibility: String = "public",
        @Field("spoiler_text") spoilerText: String? = null,
        @Field("sensitive") sensitive: Boolean? = null,
        @Field("media_ids[]") mediaIds: List<String>? = null,
    ): Status

    @DELETE("api/v1/statuses/{id}")
    suspend fun deleteStatus(@Path("id") id: String)

    @POST("api/v1/statuses/{id}/favourite")
    suspend fun favourite(@Path("id") id: String): Status

    @POST("api/v1/statuses/{id}/unfavourite")
    suspend fun unfavourite(@Path("id") id: String): Status

    @POST("api/v1/statuses/{id}/reblog")
    suspend fun reblog(@Path("id") id: String): Status

    @POST("api/v1/statuses/{id}/unreblog")
    suspend fun unreblog(@Path("id") id: String): Status

    @POST("api/v1/statuses/{id}/bookmark")
    suspend fun bookmark(@Path("id") id: String): Status

    @POST("api/v1/statuses/{id}/unbookmark")
    suspend fun unbookmark(@Path("id") id: String): Status

    // -- Notifications -----------------------------------------------------

    @GET("api/v1/notifications")
    suspend fun notifications(@Query("max_id") maxId: String? = null): Response<List<Notification>>

    @POST("api/v1/notifications/clear")
    suspend fun clearNotifications()

    @POST("api/v1/notifications/{id}/dismiss")
    suspend fun dismissNotification(@Path("id") id: String)

    // -- Conversations (DMs) ------------------------------------------------

    @GET("api/v1/conversations")
    suspend fun conversations(@Query("max_id") maxId: String? = null): Response<List<Conversation>>

    // -- Media ---------------------------------------------------------------

    @Multipart
    @POST("api/v1/media")
    suspend fun uploadMedia(
        @Part file: MultipartBody.Part,
        @Part("description") description: RequestBody? = null,
    ): MediaAttachment

    // -- Instance ------------------------------------------------------------

    @GET("api/v1/instance")
    suspend fun instance(): Instance
}
