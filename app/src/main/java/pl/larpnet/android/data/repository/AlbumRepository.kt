package pl.larpnet.android.data.repository

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import pl.larpnet.android.data.model.FriendicaPhoto
import pl.larpnet.android.data.model.FriendicaPhotoAlbum
import pl.larpnet.android.network.FriendicaApi
import pl.larpnet.android.network.safeApiCall

class AlbumRepository(private val apiProvider: () -> FriendicaApi) {

    suspend fun albums(): Result<List<FriendicaPhotoAlbum>> = safeApiCall { apiProvider().photoAlbums() }

    suspend fun photos(album: String): Result<List<FriendicaPhoto>> = safeApiCall { apiProvider().photosInAlbum(album) }

    suspend fun uploadPhoto(
        album: String,
        bytes: ByteArray,
        mimeType: String,
        fileName: String,
        description: String? = null,
    ): Result<FriendicaPhoto> = safeApiCall {
        val body = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("media", fileName, body)
        val albumBody = album.toRequestBody("text/plain".toMediaTypeOrNull())
        val descBody = description?.takeIf { it.isNotBlank() }?.toRequestBody("text/plain".toMediaTypeOrNull())
        apiProvider().createPhoto(part, albumBody, descBody)
    }

    suspend fun deletePhoto(photoId: String): Result<Unit> = safeApiCall { apiProvider().deletePhoto(photoId) }
}
