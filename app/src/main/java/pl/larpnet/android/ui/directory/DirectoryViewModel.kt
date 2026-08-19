package pl.larpnet.android.ui.directory

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pl.larpnet.android.data.model.Account
import pl.larpnet.android.data.model.Relationship
import pl.larpnet.android.data.repository.ProfileRepository

private const val PAGE_SIZE = 40

data class DirectoryUiState(
    val accounts: List<Account> = emptyList(),
    val relationships: Map<String, Relationship> = emptyMap(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val error: String? = null,
)

/**
 * All accounts local to this Larpnet instance (`/api/v1/directory?local=true`). Unlike
 * timelines/search, this endpoint has no `Link` pagination header (Directory.php just does
 * `jsonExit`), so paging is done by tracking a running offset and treating a short page
 * (< [PAGE_SIZE]) as the end.
 */
class DirectoryViewModel(private val profileRepository: ProfileRepository) : ViewModel() {

    var uiState by mutableStateOf(DirectoryUiState())
        private set

    private var nextOffset = 0

    init {
        loadInitial()
    }

    fun loadInitial() {
        uiState = uiState.copy(isLoading = true, error = null)
        nextOffset = 0
        viewModelScope.launch {
            profileRepository.directory(offset = 0).fold(
                onSuccess = { accounts ->
                    nextOffset = accounts.size
                    uiState = uiState.copy(
                        accounts = accounts,
                        isLoading = false,
                        canLoadMore = accounts.size >= PAGE_SIZE,
                    )
                    loadRelationships(accounts.map { it.id })
                },
                onFailure = { e -> uiState = uiState.copy(isLoading = false, error = e.message) },
            )
        }
    }

    fun loadMore() {
        if (uiState.isLoadingMore || !uiState.canLoadMore) return
        uiState = uiState.copy(isLoadingMore = true)
        viewModelScope.launch {
            profileRepository.directory(offset = nextOffset).fold(
                onSuccess = { accounts ->
                    nextOffset += accounts.size
                    uiState = uiState.copy(
                        accounts = uiState.accounts + accounts,
                        isLoadingMore = false,
                        canLoadMore = accounts.size >= PAGE_SIZE,
                    )
                    loadRelationships(accounts.map { it.id })
                },
                onFailure = { e -> uiState = uiState.copy(isLoadingMore = false, error = e.message) },
            )
        }
    }

    private fun loadRelationships(ids: List<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            profileRepository.relationships(ids).onSuccess { relationships ->
                uiState = uiState.copy(relationships = uiState.relationships + relationships.associateBy { it.id })
            }
        }
    }

    fun toggleFollow(account: Account) {
        val relationship = uiState.relationships[account.id]
        // A pending request (locked account, not yet approved) must unfollow to cancel it,
        // not follow again -- following==false is true in both the "not following" and
        // "requested" states, so requested has to be checked too.
        val newValue = relationship?.following != true && relationship?.requested != true
        // Optimistic update so the button changes immediately instead of only after the
        // round-trip. Locked accounts land in "requested", not "following", until approved.
        uiState = uiState.copy(
            relationships = uiState.relationships + (account.id to (relationship ?: Relationship(id = account.id)).copy(
                following = newValue && !account.locked,
                requested = newValue && account.locked,
            )),
        )
        viewModelScope.launch {
            profileRepository.setFollowing(account.id, newValue).onSuccess { updated ->
                uiState = uiState.copy(relationships = uiState.relationships + (updated.id to updated))
            }
        }
    }
}
