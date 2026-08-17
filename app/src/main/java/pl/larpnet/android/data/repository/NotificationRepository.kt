package pl.larpnet.android.data.repository

import pl.larpnet.android.data.model.Notification
import pl.larpnet.android.network.FriendicaApi
import pl.larpnet.android.network.Page
import pl.larpnet.android.network.parseLinkHeader
import pl.larpnet.android.network.safeApiCall

class NotificationRepository(private val apiProvider: () -> FriendicaApi) {

    suspend fun list(maxId: String? = null): Result<Page<Notification>> = safeApiCall {
        parseLinkHeader(apiProvider().notifications(maxId))
    }

    suspend fun clearAll(): Result<Unit> = safeApiCall { apiProvider().clearNotifications() }

    suspend fun dismiss(id: String): Result<Unit> = safeApiCall { apiProvider().dismissNotification(id) }
}
