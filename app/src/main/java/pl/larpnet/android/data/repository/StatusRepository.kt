package pl.larpnet.android.data.repository

import pl.larpnet.android.data.model.Status
import pl.larpnet.android.data.model.StatusContext
import pl.larpnet.android.network.FriendicaApi
import pl.larpnet.android.network.safeApiCall

class StatusRepository(private val apiProvider: () -> FriendicaApi) {

    suspend fun get(id: String): Result<Status> = safeApiCall { apiProvider().getStatus(id) }

    suspend fun context(id: String): Result<StatusContext> = safeApiCall { apiProvider().getContext(id) }

    suspend fun post(
        text: String,
        inReplyToId: String? = null,
        visibility: String = "public",
        spoilerText: String? = null,
        sensitive: Boolean = false,
        mediaIds: List<String> = emptyList(),
    ): Result<Status> = safeApiCall {
        apiProvider().postStatus(
            status = text,
            inReplyToId = inReplyToId,
            visibility = visibility,
            spoilerText = spoilerText?.takeIf { it.isNotBlank() },
            sensitive = sensitive,
            mediaIds = mediaIds.ifEmpty { null },
        )
    }

    /**
     * Posts through the legacy Twitter-compat endpoint so [circleGids]/[contactCids] can be
     * sent at all -- see [FriendicaApi.postStatusWithAudience]'s doc comment. Re-fetches via
     * the normal Mastodon [FriendicaApi.getStatus] afterward so callers get back the same
     * [Status] shape every other posting path returns, rather than needing to understand the
     * legacy endpoint's Twitter-shaped response.
     */
    suspend fun postWithAudience(
        text: String,
        inReplyToId: String?,
        spoilerText: String?,
        mediaIds: List<String>,
        circleGids: Set<Int>,
        contactCids: Set<Int>,
    ): Result<Status> = safeApiCall {
        val ref = apiProvider().postStatusWithAudience(
            status = text,
            inReplyToStatusId = inReplyToId,
            title = spoilerText?.takeIf { it.isNotBlank() },
            contactAllow = contactCids.map { it.toString() }.ifEmpty { null },
            circleAllow = circleGids.map { it.toString() }.ifEmpty { null },
            mediaIds = mediaIds.joinToString(",").ifBlank { null },
        )
        apiProvider().getStatus(ref.idStr)
    }

    suspend fun delete(id: String): Result<Unit> = safeApiCall { apiProvider().deleteStatus(id) }

    suspend fun setFavourited(id: String, favourited: Boolean): Result<Status> = safeApiCall {
        if (favourited) apiProvider().favourite(id) else apiProvider().unfavourite(id)
    }

    suspend fun setReblogged(id: String, reblogged: Boolean): Result<Status> = safeApiCall {
        if (reblogged) apiProvider().reblog(id) else apiProvider().unreblog(id)
    }

    suspend fun setBookmarked(id: String, bookmarked: Boolean): Result<Status> = safeApiCall {
        if (bookmarked) apiProvider().bookmark(id) else apiProvider().unbookmark(id)
    }
}
