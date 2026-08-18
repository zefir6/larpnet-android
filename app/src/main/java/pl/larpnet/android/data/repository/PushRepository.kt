package pl.larpnet.android.data.repository

import pl.larpnet.android.data.model.PushConfig
import pl.larpnet.android.network.FriendicaApi
import pl.larpnet.android.network.safeApiCall

class PushRepository(private val apiProvider: () -> FriendicaApi) {
    suspend fun config(): Result<PushConfig> = safeApiCall { apiProvider().larpnetPushConfig() }
}
