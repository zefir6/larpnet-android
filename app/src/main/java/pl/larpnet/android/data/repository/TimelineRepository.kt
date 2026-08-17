package pl.larpnet.android.data.repository

import pl.larpnet.android.data.model.Status
import pl.larpnet.android.network.FriendicaApi
import pl.larpnet.android.network.Page
import pl.larpnet.android.network.parseLinkHeader
import pl.larpnet.android.network.safeApiCall

class TimelineRepository(private val apiProvider: () -> FriendicaApi) {

    suspend fun home(maxId: String? = null, sinceId: String? = null): Result<Page<Status>> = safeApiCall {
        parseLinkHeader(apiProvider().homeTimeline(maxId = maxId, sinceId = sinceId))
    }

    suspend fun public(maxId: String? = null, sinceId: String? = null, local: Boolean = false): Result<Page<Status>> = safeApiCall {
        parseLinkHeader(apiProvider().publicTimeline(maxId = maxId, sinceId = sinceId, local = local))
    }

    suspend fun tag(hashtag: String, maxId: String? = null): Result<Page<Status>> = safeApiCall {
        parseLinkHeader(apiProvider().tagTimeline(hashtag, maxId))
    }

    suspend fun accountStatuses(accountId: String, maxId: String? = null): Result<Page<Status>> = safeApiCall {
        parseLinkHeader(apiProvider().getAccountStatuses(accountId, maxId))
    }
}
