package pl.larpnet.android.data.repository

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import pl.larpnet.android.data.model.Account
import pl.larpnet.android.data.model.Circle
import pl.larpnet.android.data.model.FollowerEntry
import pl.larpnet.android.data.model.Preferences
import pl.larpnet.android.data.model.Relationship
import pl.larpnet.android.network.FriendicaApi
import pl.larpnet.android.network.NetworkError
import pl.larpnet.android.network.Page
import pl.larpnet.android.network.parseLinkHeader
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

    suspend fun block(id: String): Result<Relationship> = safeApiCall { apiProvider().block(id) }

    suspend fun unblock(id: String): Result<Relationship> = safeApiCall { apiProvider().unblock(id) }

    suspend fun blockedAccounts(maxId: String? = null): Result<Page<Account>> = safeApiCall {
        parseLinkHeader(apiProvider().blockedAccounts(maxId))
    }

    suspend fun report(
        accountId: String,
        comment: String?,
        category: String,
        statusIds: List<String> = emptyList(),
    ): Result<Unit> = safeApiCall {
        apiProvider().report(accountId, comment?.takeIf { it.isNotBlank() }, category, statusIds.ifEmpty { null })
    }

    /**
     * Uploads through the legacy [FriendicaApi.updateProfileImage] endpoint, then byte-verifies
     * the change actually landed server-side before declaring success: fetches the current
     * avatar bytes (cache-busted), uploads, fetches the new avatar URL's bytes the same way, and
     * compares. A server that accepts the multipart POST but silently no-ops (confirmed to
     * happen intermittently against this server) would otherwise look like a successful upload.
     */
    suspend fun uploadAvatar(bytes: ByteArray, mimeType: String, fileName: String): Result<Account> {
        val beforeUrl = safeApiCall { apiProvider().verifyCredentials().avatar }.getOrElse { return Result.failure(it) }
        val beforeBytes = beforeUrl.takeIf { it.isNotBlank() }
            ?.let { runCatching { apiProvider().fetchBytes(cacheBusted(it)).bytes() }.getOrNull() }

        val uploadResult = safeApiCall {
            val body = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("image", fileName, body)
            apiProvider().updateProfileImage(part)
            apiProvider().verifyCredentials()
        }
        val after = uploadResult.getOrElse { return Result.failure(it) }

        val afterBytes = after.avatar.takeIf { it.isNotBlank() }
            ?.let { runCatching { apiProvider().fetchBytes(cacheBusted(it)).bytes() }.getOrNull() }

        if (beforeBytes != null && afterBytes != null && beforeBytes.contentEquals(afterBytes)) {
            return Result.failure(NetworkError.AvatarUploadUnverified)
        }
        return Result.success(after)
    }

    private fun cacheBusted(url: String): String {
        val separator = if ("?" in url) "&" else "?"
        return "$url${separator}_cb=${System.currentTimeMillis()}"
    }
}
