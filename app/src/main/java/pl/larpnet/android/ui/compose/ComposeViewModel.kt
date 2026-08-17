package pl.larpnet.android.ui.compose

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
import pl.larpnet.android.data.model.MediaAttachment
import pl.larpnet.android.data.repository.MediaRepository
import pl.larpnet.android.data.repository.StatusRepository

data class ComposeUiState(
    val replyToId: String? = null,
    val text: String = "",
    val spoilerText: String = "",
    val visibility: String = "public",
    val sensitive: Boolean = false,
    val mediaAttachments: List<MediaAttachment> = emptyList(),
    val isUploadingMedia: Boolean = false,
    val isPosting: Boolean = false,
    val error: String? = null,
    val posted: Boolean = false,
) {
    val canPublish: Boolean
        get() = (text.isNotBlank() || mediaAttachments.isNotEmpty()) && !isPosting && !isUploadingMedia
}

class ComposeViewModel(
    replyToId: String?,
    private val statusRepository: StatusRepository,
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    var uiState by mutableStateOf(ComposeUiState(replyToId = replyToId))
        private set

    fun onTextChange(value: String) {
        uiState = uiState.copy(text = value)
    }

    fun onSpoilerTextChange(value: String) {
        uiState = uiState.copy(spoilerText = value)
    }

    fun onVisibilityChange(value: String) {
        uiState = uiState.copy(visibility = value)
    }

    fun onSensitiveChange(value: Boolean) {
        uiState = uiState.copy(sensitive = value)
    }

    fun addMedia(context: Context, uri: Uri) {
        uiState = uiState.copy(isUploadingMedia = true, error = null)
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            if (bytes == null) {
                uiState = uiState.copy(isUploadingMedia = false, error = "media_read_failed")
                return@launch
            }
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            mediaRepository.upload(bytes, mimeType, fileName = "upload.jpg").fold(
                onSuccess = { media ->
                    uiState = uiState.copy(
                        isUploadingMedia = false,
                        mediaAttachments = uiState.mediaAttachments + media,
                    )
                },
                onFailure = { e -> uiState = uiState.copy(isUploadingMedia = false, error = e.message) },
            )
        }
    }

    fun removeMedia(mediaId: String) {
        uiState = uiState.copy(mediaAttachments = uiState.mediaAttachments.filterNot { it.id == mediaId })
    }

    fun publish() {
        if (!uiState.canPublish) return
        uiState = uiState.copy(isPosting = true, error = null)
        viewModelScope.launch {
            statusRepository.post(
                text = uiState.text,
                inReplyToId = uiState.replyToId,
                visibility = uiState.visibility,
                spoilerText = uiState.spoilerText,
                sensitive = uiState.sensitive,
                mediaIds = uiState.mediaAttachments.map { it.id },
            ).fold(
                onSuccess = { uiState = uiState.copy(isPosting = false, posted = true) },
                onFailure = { e -> uiState = uiState.copy(isPosting = false, error = e.message) },
            )
        }
    }
}
