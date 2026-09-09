package pl.larpnet.android.ui.albums

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pl.larpnet.android.data.model.FriendicaPhotoAlbum
import pl.larpnet.android.data.repository.AlbumRepository

data class AlbumsUiState(
    val albums: List<FriendicaPhotoAlbum> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

class AlbumsViewModel(private val albumRepository: AlbumRepository) : ViewModel() {

    var uiState by mutableStateOf(AlbumsUiState())
        private set

    init {
        load()
    }

    fun load() {
        uiState = uiState.copy(isLoading = true, error = null)
        viewModelScope.launch {
            albumRepository.albums().fold(
                onSuccess = { uiState = uiState.copy(albums = it, isLoading = false) },
                onFailure = { e -> uiState = uiState.copy(isLoading = false, error = e.message) },
            )
        }
    }

    /** There's no album-creation endpoint -- uploading the first photo with a new album name
     * implicitly creates it server-side (see FriendicaApi.photoAlbums's doc comment). This adds
     * a zero-count placeholder so the new album is immediately visible/tappable, before any
     * photo has actually landed in it. */
    fun addLocalPlaceholder(name: String) {
        if (uiState.albums.any { it.name.equals(name, ignoreCase = true) }) return
        uiState = uiState.copy(albums = uiState.albums + FriendicaPhotoAlbum(name, count = 0))
    }
}
