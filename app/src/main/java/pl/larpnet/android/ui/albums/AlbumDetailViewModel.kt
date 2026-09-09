package pl.larpnet.android.ui.albums

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.larpnet.android.data.model.FriendicaPhoto
import pl.larpnet.android.data.repository.AlbumRepository

data class AlbumDetailUiState(
    val photos: List<FriendicaPhoto> = emptyList(),
    val isLoading: Boolean = true,
    val isUploading: Boolean = false,
    val error: String? = null,
)

class AlbumDetailViewModel(private val album: String, private val albumRepository: AlbumRepository) : ViewModel() {

    var uiState by mutableStateOf(AlbumDetailUiState())
        private set

    init {
        load()
    }

    fun load() {
        uiState = uiState.copy(isLoading = true, error = null)
        viewModelScope.launch {
            albumRepository.photos(album).fold(
                onSuccess = { uiState = uiState.copy(photos = it, isLoading = false) },
                onFailure = { e -> uiState = uiState.copy(isLoading = false, error = e.message) },
            )
        }
    }

    /** Uploads sequentially, not concurrently -- mirrors the iOS app's AlbumDetailViewModel,
     * and keeps a failure partway through easy to reason about (everything before it is
     * already uploaded and visible). */
    fun uploadPhotos(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        uiState = uiState.copy(isUploading = true, error = null)
        viewModelScope.launch {
            for (uri in uris) {
                val bytes = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                } ?: continue
                val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                albumRepository.uploadPhoto(album, bytes, mimeType, fileName = "upload.jpg").fold(
                    onSuccess = { photo -> uiState = uiState.copy(photos = uiState.photos + photo) },
                    onFailure = { e -> uiState = uiState.copy(error = e.message) },
                )
            }
            uiState = uiState.copy(isUploading = false)
        }
    }

    fun deletePhoto(photo: FriendicaPhoto) {
        uiState = uiState.copy(photos = uiState.photos.filterNot { it.id == photo.id })
        viewModelScope.launch { albumRepository.deletePhoto(photo.id) }
    }
}
