package pl.larpnet.android.data.repository

import pl.larpnet.android.data.model.Account
import pl.larpnet.android.data.model.Circle
import pl.larpnet.android.data.model.FollowerEntry
import pl.larpnet.android.data.model.Preferences
import pl.larpnet.android.data.model.Relationship
import pl.larpnet.android.network.FriendicaApi
import pl.larpnet.android.network.safeApiCall

class ProfileRepository(private val apiProvider: () -> FriendicaApi) {

    suspend fun me(): Result<Account> = safeApiCall { apiProvider().verifyCredentials() }

    suspend fun preferences(): Result<Preferences> = safeApiCall { apiProvider().preferences() }

    suspend fun account(id: String): Result<Account> = safeApiCall { apiProvider().getAccount(id) }

    suspend fun updateProfile(
        displayName: String?,
        note: String?,
        locked: Boolean? = null,
        discoverable: Boolean? = null,
        bot: Boolean? = null,
    ): Result<Account> = safeApiCall {
        apiProvider().updateCredentials(displayName, note, locked, discoverable, bot)
    }

    suspend fun relationship(id: String): Result<Relationship?> = safeApiCall {
        apiProvider().relationships(listOf(id)).firstOrNull()
    }

    suspend fun relationships(ids: List<String>): Result<List<Relationship>> = safeApiCall {
        if (ids.isEmpty()) emptyList() else apiProvider().relationships(ids)
    }

    suspend fun setFollowing(id: String, following: Boolean): Result<Relationship> = safeApiCall {
        if (following) apiProvider().follow(id) else apiProvider().unfollow(id)
    }

    /** [resolve] triggers a WebFinger lookup server-side, only useful when [query] is an exact `user@domain` handle. */
    suspend fun search(query: String, resolve: Boolean = false): Result<List<Account>> = safeApiCall {
        apiProvider().searchAccounts(query, resolve = resolve)
    }

    /** All accounts local to this instance, sorted by most recent activity. Pages via offset -- see [FriendicaApi.directory]. */
    suspend fun directory(offset: Int): Result<List<Account>> = safeApiCall {
        apiProvider().directory(offset = offset, local = true)
    }

    /** For the compose screen's custom-audience picker -- see [FriendicaApi.circles]. */
    suspend fun circles(): Result<List<Circle>> = safeApiCall { apiProvider().circles() }

    /** For the compose screen's custom-audience picker -- see [FriendicaApi.followersList]. */
    suspend fun followers(): Result<List<FollowerEntry>> = safeApiCall { apiProvider().followersList().users }
}
