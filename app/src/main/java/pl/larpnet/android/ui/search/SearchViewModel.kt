package pl.larpnet.android.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.larpnet.android.data.model.Account
import pl.larpnet.android.data.model.Relationship
import pl.larpnet.android.data.repository.ProfileRepository

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<Account> = emptyList(),
    val relationships: Map<String, Relationship> = emptyMap(),
    val error: String? = null,
    val hasSearched: Boolean = false,
)

/**
 * Debounces query changes (300ms) before hitting `/api/v1/accounts/search` -- that endpoint
 * has no Link-header pagination server-side (Accounts/Search.php just does `jsonExit`), so
 * results are a single page capped by [FriendicaApi.searchAccounts]'s limit, no load-more.
 */
class SearchViewModel(private val profileRepository: ProfileRepository) : ViewModel() {

    var uiState by mutableStateOf(SearchUiState())
        private set

    private var searchJob: Job? = null

    fun onQueryChange(value: String) {
        uiState = uiState.copy(query = value)
        searchJob?.cancel()
        if (value.isBlank()) {
            uiState = uiState.copy(results = emptyList(), isSearching = false, hasSearched = false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            runSearch(value)
        }
    }

    private suspend fun runSearch(query: String) {
        uiState = uiState.copy(isSearching = true, error = null)
        // An exact `user@domain` handle is worth a WebFinger resolve so results include
        // accounts this instance hasn't seen before, matching what typing a full handle
        // into Mastodon's own search box does.
        val resolve = query.contains('@') && query.substringAfterLast('@').contains('.')
        profileRepository.search(query, resolve = resolve).fold(
            onSuccess = { accounts ->
                uiState = uiState.copy(results = accounts, isSearching = false, hasSearched = true)
                loadRelationships(accounts.map { it.id })
            },
            onFailure = { e -> uiState = uiState.copy(isSearching = false, hasSearched = true, error = e.message) },
        )
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
        val current = uiState.relationships[account.id]
        val newValue = current?.following != true
        viewModelScope.launch {
            profileRepository.setFollowing(account.id, newValue).onSuccess { updated ->
                uiState = uiState.copy(relationships = uiState.relationships + (updated.id to updated))
            }
        }
    }
}
