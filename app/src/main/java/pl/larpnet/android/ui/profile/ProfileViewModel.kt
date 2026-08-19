package pl.larpnet.android.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pl.larpnet.android.data.model.Account
import pl.larpnet.android.data.model.Relationship
import pl.larpnet.android.data.model.Status
import pl.larpnet.android.data.repository.AuthRepository
import pl.larpnet.android.data.repository.ProfileRepository
import pl.larpnet.android.data.repository.StatusRepository
import pl.larpnet.android.data.repository.TimelineRepository

data class ProfileUiState(
    val isOwn: Boolean,
    val account: Account? = null,
    val relationship: Relationship? = null,
    val statuses: List<Status> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val error: String? = null,
)

/** [accountId] null means "the logged-in user's own profile". */
class ProfileViewModel(
    private val accountId: String?,
    private val profileRepository: ProfileRepository,
    private val timelineRepository: TimelineRepository,
    private val statusRepository: StatusRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    var uiState by mutableStateOf(ProfileUiState(isOwn = accountId == null))
        private set

    private var nextMaxId: String? = null
    private var resolvedAccountId: String? = accountId

    init {
        load()
    }

    fun load() {
        uiState = uiState.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val accountResult = if (accountId == null) profileRepository.me() else profileRepository.account(accountId)
            val account = accountResult.getOrElse {
                uiState = uiState.copy(isLoading = false, error = it.message)
                return@launch
            }
            resolvedAccountId = account.id

            val relationship = if (accountId != null) {
                profileRepository.relationship(account.id).getOrNull()
            } else {
                null
            }

            timelineRepository.accountStatuses(account.id, maxId = null).fold(
                onSuccess = { page ->
                    nextMaxId = page.nextMaxId
                    uiState = uiState.copy(
                        account = account,
                        relationship = relationship,
                        statuses = page.items,
                        isLoading = false,
                        canLoadMore = page.nextMaxId != null,
                    )
                },
                onFailure = { e ->
                    uiState = uiState.copy(
                        account = account,
                        relationship = relationship,
                        isLoading = false,
                        error = e.message,
                    )
                },
            )
        }
    }

    fun loadMore() {
        val id = resolvedAccountId ?: return
        if (uiState.isLoadingMore || !uiState.canLoadMore) return
        val cursor = nextMaxId ?: return
        uiState = uiState.copy(isLoadingMore = true)
        viewModelScope.launch {
            timelineRepository.accountStatuses(id, maxId = cursor).fold(
                onSuccess = { page ->
                    nextMaxId = page.nextMaxId
                    uiState = uiState.copy(
                        statuses = uiState.statuses + page.items,
                        isLoadingMore = false,
                        canLoadMore = page.nextMaxId != null,
                    )
                },
                onFailure = { e -> uiState = uiState.copy(isLoadingMore = false, error = e.message) },
            )
        }
    }

    fun toggleFollow() {
        val account = uiState.account ?: return
        // Deliberately not requiring uiState.relationship to be non-null: the button is
        // shown (defaulting to "Follow") even before/if that fetch finishes, so bailing out
        // here on a null relationship made the button silently do nothing on click.
        val newValue = uiState.relationship?.following != true
        viewModelScope.launch {
            profileRepository.setFollowing(account.id, newValue).onSuccess { updated ->
                uiState = uiState.copy(relationship = updated)
            }
        }
    }

    fun toggleFavourite(status: Status) {
        val newValue = !status.favourited
        updateStatus(status.id) { it.copy(favourited = newValue, favouritesCount = it.favouritesCount + if (newValue) 1 else -1) }
        viewModelScope.launch {
            statusRepository.setFavourited(status.id, newValue).onSuccess { updated -> updateStatus(updated.id) { updated } }
        }
    }

    fun toggleReblog(status: Status) {
        val newValue = !status.reblogged
        updateStatus(status.id) { it.copy(reblogged = newValue, reblogsCount = it.reblogsCount + if (newValue) 1 else -1) }
        viewModelScope.launch {
            statusRepository.setReblogged(status.id, newValue).onSuccess { updated -> updateStatus(updated.id) { updated } }
        }
    }

    fun toggleBookmark(status: Status) {
        val newValue = !status.bookmarked
        updateStatus(status.id) { it.copy(bookmarked = newValue) }
        viewModelScope.launch {
            statusRepository.setBookmarked(status.id, newValue).onSuccess { updated -> updateStatus(updated.id) { updated } }
        }
    }

    /** See TimelineViewModel.applyLocalUpdate's doc comment: for a boosted status, [id] is the
     * reblogged post's own id, not the top-level item's -- must check both. */
    private fun updateStatus(id: String, transform: (Status) -> Status) {
        uiState = uiState.copy(
            statuses = uiState.statuses.map { item ->
                when {
                    item.id == id -> transform(item)
                    item.reblog?.id == id -> item.copy(reblog = transform(item.reblog))
                    else -> item
                }
            },
        )
    }

    fun deleteStatus(status: Status) {
        viewModelScope.launch {
            statusRepository.delete(status.id).onSuccess {
                uiState = uiState.copy(statuses = uiState.statuses.filterNot { it.id == status.id })
            }
        }
    }

    fun logout() = authRepository.logout()
}
