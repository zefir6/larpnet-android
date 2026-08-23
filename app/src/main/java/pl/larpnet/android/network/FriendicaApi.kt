package pl.larpnet.android.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import pl.larpnet.android.data.model.Account
import pl.larpnet.android.data.model.Circle
import pl.larpnet.android.data.model.Conversation
import pl.larpnet.android.data.model.DirectMessage
import pl.larpnet.android.data.model.FollowerListResponse
import pl.larpnet.android.data.model.LegacyStatusRef
import pl.larpnet.android.data.model.Instance
import pl.larpnet.android.data.model.MediaAttachment
import pl.larpnet.android.data.model.Notification
import pl.larpnet.android.data.model.Preferences
import pl.larpnet.android.data.model.PushConfig
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

    /**
     * Posts through the legacy Twitter-compat API instead of the Mastodon one above: it's the
     * only endpoint that accepts an arbitrary combination of circles ([circleAllow]) and
     * individual people ([contactAllow]) as a post's audience -- the Mastodon endpoint only
     * accepts a single circle id in place of `visibility`. No `sensitive` param exists here
     * (see ComposeViewModel doc). [mediaIds] is comma-separated, not repeated fields, unlike
     * [postStatus] -- confirmed against `Module\Api\Twitter\Statuses\Update.php`. The response
     * is Twitter-shaped; callers should re-fetch via [getStatus] using [LegacyStatusRef.idStr]
     * (which is the item's uri-id) rather than parsing it further.
     */
    @FormUrlEncoded
    @POST("api/statuses/update.json")
    suspend fun postStatusWithAudience(
        @Field("status") status: String,
        @Field("in_reply_to_status_id") inReplyToStatusId: String? = null,
        @Field("title") title: String? = null,
        @Field("contact_allow[]") contactAllow: List<String>? = null,
        @Field("circle_allow[]") circleAllow: List<String>? = null,
        @Field("media_ids") mediaIds: String? = null,
    ): LegacyStatusRef

    @GET("api/friendica/circle_show.json")
    suspend fun circles(): List<Circle>

    /** [count] is capped server-side at 200 (`ContactEndpoint::MAX_COUNT`); cursor pagination beyond that is unused here. */
    @GET("api/followers/list.json")
    suspend fun followersList(@Query("count") count: Int = 200): FollowerListResponse

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
    //
    // GET/DELETE/read use the Mastodon-compatible /api/v1/conversations surface, which reads
    // and mutates Friendica's legacy `mail` table -- the same store the web UI's own Messages
    // uses. There is no Mastodon-API endpoint to *send* into that store (POST /api/v1/statuses
    // with visibility=direct is a fully separate, disconnected mechanism -- see
    // ConversationRepository), so sending and fetching one conversation's full message history
    // go through the Twitter-compat /api/direct_messages endpoints instead, which read/write
    // the same `mail` rows. Both surfaces share the same OAuth Bearer auth via BaseApi.

    @GET("api/v1/conversations")
    suspend fun conversations(@Query("max_id") maxId: String? = null): Response<List<Conversation>>

    /** Requires the server-side inverted-condition fix (see friendica-larpnet PR #14) to ever return non-422. */
    @POST("api/v1/conversations/{id}/read")
    suspend fun markConversationRead(@Path("id") id: String): Conversation

    /** Requires the server-side inverted-condition fix (see friendica-larpnet PR #14) to ever return non-422. */
    @DELETE("api/v1/conversations/{id}")
    suspend fun deleteConversation(@Path("id") id: String)

    /** All messages exchanged with one contact (both directions), newest first, Link-header paginated. */
    @GET("api/direct_messages/all.json")
    suspend fun directMessages(
        @Query("profileurl") profileUrl: String,
        @Query("count") count: Int = 40,
        @Query("max_id") maxId: String? = null,
    ): Response<List<DirectMessage>>

    /**
     * NewDM.php's own early guard is `empty($text) || empty($screen_name) && empty($user_id)`
     * (note the `&&` binds tighter than `||`) -- it only checks for the presence of
     * `screen_name` or `user_id`, never `profileurl`, even though `profileurl` is a valid
     * resolvable identifier further down in getContactIDForSearchterm(). Passing only
     * `profileurl` trips that guard and the endpoint silently no-ops (200, empty body) --
     * confirmed live. `screen_name` (nickname, or `nick@host` for remote accounts -- both
     * handled by getContactIDForSearchterm) sidesteps it entirely.
     */
    @FormUrlEncoded
    @POST("api/direct_messages/new.json")
    suspend fun sendDirectMessage(
        @Field("screen_name") screenName: String,
        @Field("text") text: String,
    ): DirectMessage

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

    // -- Push notifications ---------------------------------------------------

    /** Server-side native-client counterpart of the larpnet_notifications theme's browser push setup. */
    @GET("api/v1/larpnet_push_config")
    suspend fun larpnetPushConfig(): PushConfig
}
