package pl.larpnet.android.ui.following

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pl.larpnet.android.data.model.Status
import pl.larpnet.android.data.repository.StatusRepository

data class FollowedThreadEntry(val rootStatusId: String, val rootStatus: Status?, val unreadCount: Int)

data class FollowedThreadsUiState(val entries: List<FollowedThreadEntry> = emptyList(), val isLoading: Boolean = true)

/**
 * Issues a status + context fetch per followed thread on every load (there's no batched
 * "give me unread counts for these N threads" endpoint) -- fine for the handful of threads a
 * user is likely to follow, same tradeoff the iOS app accepted for the same feature.
 */
class FollowedThreadsViewModel(
    private val followedThreadsStore: FollowedThreadsStore,
    private val statusRepository: StatusRepository,
) : ViewModel() {

    var uiState by mutableStateOf(FollowedThreadsUiState())
        private set

    init {
        viewModelScope.launch {
            followedThreadsStore.threads.collect { threads -> load(threads) }
        }
    }

    private suspend fun load(threads: List<FollowedThread>) {
        uiState = uiState.copy(isLoading = true)
        val entries = threads.sortedByDescending { it.followedAt }.map { followed ->
            val status = statusRepository.get(followed.rootStatusId).getOrNull()
            val liveReplyCount = statusRepository.context(followed.rootStatusId).getOrNull()?.descendants?.size
                ?: followed.lastSeenReplyCount
            FollowedThreadEntry(
                rootStatusId = followed.rootStatusId,
                rootStatus = status,
                unreadCount = (liveReplyCount - followed.lastSeenReplyCount).coerceAtLeast(0),
            )
        }
        uiState = uiState.copy(entries = entries, isLoading = false)
    }

    fun unfollow(rootStatusId: String) = followedThreadsStore.unfollow(rootStatusId)
}
