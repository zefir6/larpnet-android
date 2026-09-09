package pl.larpnet.android.ui.moderation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pl.larpnet.android.data.model.Account
import pl.larpnet.android.data.repository.ProfileRepository

data class BlockedAccountsUiState(
    val accounts: List<Account> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val error: String? = null,
)

class BlockedAccountsViewModel(private val profileRepository: ProfileRepository) : ViewModel() {

    var uiState by mutableStateOf(BlockedAccountsUiState())
        private set

    private var nextMaxId: String? = null

    init {
        loadInitial()
    }

    fun loadInitial() {
        uiState = uiState.copy(isLoading = true, error = null)
        viewModelScope.launch {
            profileRepository.blockedAccounts().fold(
                onSuccess = { page ->
                    nextMaxId = page.nextMaxId
                    uiState = uiState.copy(accounts = page.items, isLoading = false, canLoadMore = page.nextMaxId != null)
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
            profileRepository.blockedAccounts(cursor).fold(
                onSuccess = { page ->
                    nextMaxId = page.nextMaxId
                    uiState = uiState.copy(
                        accounts = uiState.accounts + page.items,
                        isLoadingMore = false,
                        canLoadMore = page.nextMaxId != null,
                    )
                },
                onFailure = { e -> uiState = uiState.copy(isLoadingMore = false, error = e.message) },
            )
        }
    }

    fun unblock(account: Account) {
        uiState = uiState.copy(accounts = uiState.accounts.filterNot { it.id == account.id })
        viewModelScope.launch { profileRepository.unblock(account.id) }
    }
}
