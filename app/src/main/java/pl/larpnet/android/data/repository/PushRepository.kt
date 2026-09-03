package pl.larpnet.android.data.repository

import pl.larpnet.android.data.model.PushConfig
import pl.larpnet.android.network.FriendicaApi
import pl.larpnet.android.network.safeApiCall

class PushRepository(private val apiProvider: () -> FriendicaApi) {
    suspend fun config(): Result<PushConfig> = safeApiCall { apiProvider().larpnetPushConfig() }

    suspend fun registerFcmToken(token: String): Result<Unit> =
        safeApiCall { apiProvider().registerFcmToken(token) }.map {}

    suspend fun unregisterFcmToken(token: String): Result<Unit> =
        safeApiCall { apiProvider().registerFcmToken(token, unregister = 1) }.map {}
}
