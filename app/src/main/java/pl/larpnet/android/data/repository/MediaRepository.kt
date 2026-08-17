package pl.larpnet.android.data.repository

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import pl.larpnet.android.data.model.MediaAttachment
import pl.larpnet.android.network.FriendicaApi
import pl.larpnet.android.network.safeApiCall

class MediaRepository(private val apiProvider: () -> FriendicaApi) {

    suspend fun upload(
        bytes: ByteArray,
        mimeType: String,
        fileName: String,
        description: String? = null,
    ): Result<MediaAttachment> = safeApiCall {
        val body = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", fileName, body)
        val descriptionBody = description?.takeIf { it.isNotBlank() }
            ?.toRequestBody("text/plain".toMediaTypeOrNull())
        apiProvider().uploadMedia(part, descriptionBody)
    }
}
