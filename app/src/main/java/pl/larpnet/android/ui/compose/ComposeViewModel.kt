package pl.larpnet.android.ui.compose

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.larpnet.android.data.model.Circle
import pl.larpnet.android.data.model.FollowerEntry
import pl.larpnet.android.data.model.MediaAttachment
import pl.larpnet.android.data.repository.MediaRepository
import pl.larpnet.android.data.repository.ProfileRepository
import pl.larpnet.android.data.repository.StatusRepository

/** [visibility] is "custom" as a compose-only sentinel (like "direct", see VisibilityIcon.kt)
 * when the user has picked a hand-built audience via [ComposeViewModel.confirmAudience] --
 * the actual selection lives in [customAudienceCircles]/[customAudienceContacts]. */
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
    val showAudiencePicker: Boolean = false,
    val isLoadingAudiencePicker: Boolean = false,
    val availableCircles: List<Circle> = emptyList(),
    val availableFollowers: List<FollowerEntry> = emptyList(),
    val customAudienceCircles: Set<Int> = emptySet(),
    val customAudienceContacts: Set<Int> = emptySet(),
) {
    val canPublish: Boolean
        get() = (text.isNotBlank() || mediaAttachments.isNotEmpty()) && !isPosting && !isUploadingMedia
}

class ComposeViewModel(
    replyToId: String?,
    private val statusRepository: StatusRepository,
    private val mediaRepository: MediaRepository,
    private val profileRepository: ProfileRepository,
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
        uiState = uiState.copy(
            visibility = value,
            customAudienceCircles = emptySet(),
            customAudienceContacts = emptySet(),
        )
    }

    /** Opens the picker, lazily loading circles/followers once per compose session (not re-fetched on reopen). */
    fun openAudiencePicker() {
        uiState = uiState.copy(showAudiencePicker = true)
        if (uiState.availableCircles.isNotEmpty() || uiState.availableFollowers.isNotEmpty()) return
        uiState = uiState.copy(isLoadingAudiencePicker = true)
        viewModelScope.launch {
            val circlesDeferred = async { profileRepository.circles() }
            val followersDeferred = async { profileRepository.followers() }
            val circlesResult = circlesDeferred.await()
            val followersResult = followersDeferred.await()
            uiState = uiState.copy(
                isLoadingAudiencePicker = false,
                availableCircles = circlesResult.getOrDefault(emptyList()),
                availableFollowers = followersResult.getOrDefault(emptyList()),
            )
        }
    }

    fun dismissAudiencePicker() {
        uiState = uiState.copy(showAudiencePicker = false)
    }

    fun toggleCircle(gid: Int) {
        val current = uiState.customAudienceCircles
        uiState = uiState.copy(customAudienceCircles = if (gid in current) current - gid else current + gid)
    }

    fun toggleContact(cid: Int) {
        val current = uiState.customAudienceContacts
        uiState = uiState.copy(customAudienceContacts = if (cid in current) current - cid else current + cid)
    }

    /** Only called when the picker's Confirm button is enabled, which requires a non-empty
     * selection -- an empty allow_cid+allow_gid means PUBLIC server-side, not "nobody". */
    fun confirmAudience() {
        uiState = uiState.copy(visibility = "custom", showAudiencePicker = false)
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
            val result = if (uiState.visibility == "custom") {
                statusRepository.postWithAudience(
                    text = uiState.text,
                    inReplyToId = uiState.replyToId,
                    spoilerText = uiState.spoilerText,
                    mediaIds = uiState.mediaAttachments.map { it.id },
                    circleGids = uiState.customAudienceCircles,
                    contactCids = uiState.customAudienceContacts,
                )
            } else {
                statusRepository.post(
                    text = uiState.text,
                    inReplyToId = uiState.replyToId,
                    visibility = uiState.visibility,
                    spoilerText = uiState.spoilerText,
                    sensitive = uiState.sensitive,
                    mediaIds = uiState.mediaAttachments.map { it.id },
                )
            }
            result.fold(
                onSuccess = { uiState = uiState.copy(isPosting = false, posted = true) },
                onFailure = { e -> uiState = uiState.copy(isPosting = false, error = e.message) },
            )
        }
    }
}
