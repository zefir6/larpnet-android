package pl.larpnet.android.ui.timeline

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pl.larpnet.android.data.model.Status
import pl.larpnet.android.data.repository.StatusRepository
import pl.larpnet.android.data.repository.TimelineRepository

sealed interface TimelineKind {
    data object Home : TimelineKind
    /** `/api/v1/timelines/public?local=true` -- posts originating on this instance only, no federated content. */
    data object Local : TimelineKind
    data class Tag(val hashtag: String) : TimelineKind
}

data class TimelineUiState(
    val items: List<Status> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val pendingNewCount: Int = 0,
    val canLoadMore: Boolean = true,
)

/**
 * Backs a single timeline (home/public/tag). Since the server has no working streaming
 * endpoints (all /api/v1/streaming routes are Module\Api\Mastodon\Unimplemented), freshness comes
 * from polling: [startPolling] fetches periodically but buffers newly-seen posts behind a
 * "N new posts" banner (see NewPostsBanner.kt) rather than reflowing the list under the
 * reader. Pull-to-refresh ([refresh]) is a direct user action, so it merges immediately.
 */
class TimelineViewModel(
    private val kind: TimelineKind,
    private val timelineRepository: TimelineRepository,
    private val statusRepository: StatusRepository,
) : ViewModel() {

    var uiState by mutableStateOf(TimelineUiState())
        private set

    private var nextMaxId: String? = null
    private var pendingNewItems: List<Status> = emptyList()
    private var pollJob: Job? = null

    init {
        loadInitial()
    }

    private suspend fun fetchPage(maxId: String?) = when (val k = kind) {
        is TimelineKind.Home -> timelineRepository.home(maxId = maxId)
        is TimelineKind.Local -> timelineRepository.public(maxId = maxId, local = true)
        is TimelineKind.Tag -> timelineRepository.tag(k.hashtag, maxId = maxId)
    }

    fun loadInitial() {
        uiState = uiState.copy(isLoading = true, error = null)
        viewModelScope.launch {
            fetchPage(maxId = null).fold(
                onSuccess = { page ->
                    nextMaxId = page.nextMaxId
                    uiState = uiState.copy(
                        items = page.items,
                        isLoading = false,
                        canLoadMore = page.nextMaxId != null,
                    )
                },
                onFailure = { e -> uiState = uiState.copy(isLoading = false, error = e.message) },
            )
        }
    }

    fun loadMore() {
        if (uiState.isLoadingMore || !uiState.canLoadMore) return
        val cursor = nextMaxId ?: return
        uiState = uiState.copy(isLoadingMore = true)
        viewModelScope.launch {
            fetchPage(maxId = cursor).fold(
                onSuccess = { page ->
                    nextMaxId = page.nextMaxId
                    uiState = uiState.copy(
                        items = uiState.items + page.items,
                        isLoadingMore = false,
                        canLoadMore = page.nextMaxId != null,
                    )
                },
                onFailure = { e -> uiState = uiState.copy(isLoadingMore = false, error = e.message) },
            )
        }
    }

    fun refresh() {
        uiState = uiState.copy(isRefreshing = true)
        viewModelScope.launch {
            fetchPage(maxId = null).fold(
                onSuccess = { page ->
                    val existingIds = uiState.items.mapTo(mutableSetOf()) { it.id }
                    val merged = page.items.filter { it.id !in existingIds } + uiState.items
                    pendingNewItems = emptyList()
                    uiState = uiState.copy(items = merged, isRefreshing = false, pendingNewCount = 0)
                },
                onFailure = { e -> uiState = uiState.copy(isRefreshing = false, error = e.message) },
            )
        }
    }

    fun startPolling(intervalMillis: Long = 60_000) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(intervalMillis)
                fetchPage(maxId = null).onSuccess { page ->
                    val existingIds = (uiState.items.map { it.id } + pendingNewItems.map { it.id }).toSet()
                    val fresh = page.items.filter { it.id !in existingIds }
                    if (fresh.isNotEmpty()) {
                        pendingNewItems = fresh + pendingNewItems
                        uiState = uiState.copy(pendingNewCount = pendingNewItems.size)
                    }
                }
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    fun showPendingNewItems() {
        if (pendingNewItems.isEmpty()) return
        uiState = uiState.copy(items = pendingNewItems + uiState.items, pendingNewCount = 0)
        pendingNewItems = emptyList()
    }

    fun toggleFavourite(status: Status) {
        val newValue = !status.favourited
        applyLocalUpdate(status.id) {
            it.copy(favourited = newValue, favouritesCount = it.favouritesCount + if (newValue) 1 else -1)
        }
        viewModelScope.launch {
            statusRepository.setFavourited(status.id, newValue).onSuccess(::replaceStatus)
        }
    }

    fun toggleReblog(status: Status) {
        val newValue = !status.reblogged
        applyLocalUpdate(status.id) {
            it.copy(reblogged = newValue, reblogsCount = it.reblogsCount + if (newValue) 1 else -1)
        }
        viewModelScope.launch {
            statusRepository.setReblogged(status.id, newValue).onSuccess(::replaceStatus)
        }
    }

    fun toggleBookmark(status: Status) {
        val newValue = !status.bookmarked
        applyLocalUpdate(status.id) { it.copy(bookmarked = newValue) }
        viewModelScope.launch {
            statusRepository.setBookmarked(status.id, newValue).onSuccess(::replaceStatus)
        }
    }

    private fun applyLocalUpdate(statusId: String, transform: (Status) -> Status) {
        uiState = uiState.copy(items = uiState.items.map { if (it.id == statusId) transform(it) else it })
    }

    private fun replaceStatus(updated: Status) = applyLocalUpdate(updated.id) { updated }

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }
}
