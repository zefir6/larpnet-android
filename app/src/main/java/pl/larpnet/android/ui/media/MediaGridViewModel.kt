package pl.larpnet.android.ui.media

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pl.larpnet.android.data.model.MediaAttachment
import pl.larpnet.android.data.repository.ProfileRepository
import pl.larpnet.android.data.repository.TimelineRepository

data class MediaGridUiState(
    val items: List<MediaAttachment> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val error: String? = null,
)

/** No dedicated "my media" endpoint -- paginates the logged-in user's own statuses (already
 * Link-header paginated) and flattens their media attachments, deduped by id, mirroring the
 * iOS app's MediaGridViewModel. */
class MediaGridViewModel(
    private val profileRepository: ProfileRepository,
    private val timelineRepository: TimelineRepository,
) : ViewModel() {

    var uiState by mutableStateOf(MediaGridUiState())
        private set

    private var accountId: String? = null
    private var nextMaxId: String? = null
    private val seenMediaIds = mutableSetOf<String>()

    init {
        load()
    }

    private fun load() {
        uiState = uiState.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val account = profileRepository.me().getOrElse {
                uiState = uiState.copy(isLoading = false, error = it.message)
                return@launch
            }
            accountId = account.id
            fetchPage(maxId = null)
        }
    }

    fun loadMore() {
        val id = accountId ?: return
        if (uiState.isLoadingMore || !uiState.canLoadMore) return
        val cursor = nextMaxId ?: return
        uiState = uiState.copy(isLoadingMore = true)
        viewModelScope.launch { fetchPage(maxId = cursor) }
    }

    private suspend fun fetchPage(maxId: String?) {
        val id = accountId ?: return
        timelineRepository.accountStatuses(id, maxId = maxId).fold(
            onSuccess = { page ->
                nextMaxId = page.nextMaxId
                val newMedia = page.items.flatMap { it.mediaAttachments }.filter { seenMediaIds.add(it.id) }
                uiState = uiState.copy(
                    items = uiState.items + newMedia,
                    isLoading = false,
                    isLoadingMore = false,
                    canLoadMore = page.nextMaxId != null,
                )
            },
            onFailure = { e -> uiState = uiState.copy(isLoading = false, isLoadingMore = false, error = e.message) },
        )
    }
}
