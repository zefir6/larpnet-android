package pl.larpnet.android.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.larpnet.android.data.matrix.MatrixRepository
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
    /** Most-recently-active DM partners (see [MatrixRepository.suggestedDmNicknames]), shown
     * before any search text is typed. Always empty when [MatrixRepository] wasn't supplied
     * (the plain account-discovery/follow search doesn't offer these). */
    val suggestions: List<Account> = emptyList(),
)

private fun Account.matchesQuery(query: String) =
    username.contains(query, ignoreCase = true) || displayName.contains(query, ignoreCase = true)

/**
 * Debounces query changes (300ms) before hitting `/api/v1/accounts/search` -- that endpoint
 * has no Link-header pagination server-side (Accounts/Search.php just does `jsonExit`), so
 * results are a single page capped by [FriendicaApi.searchAccounts]'s limit, no load-more.
 *
 * [matrixRepository], when supplied (the new-chat recipient picker only -- see `SearchScreen`'s
 * `suggestFrequentContacts`), also seeds [SearchUiState.suggestions] with the most-recently
 * chatted-with contacts, so the picker has something useful to show before any typing, and
 * narrows that list client-side on the very first keystroke for an instant result while the
 * debounced network search below is still in flight.
 */
class SearchViewModel(
    private val profileRepository: ProfileRepository,
    private val matrixRepository: MatrixRepository? = null,
) : ViewModel() {

    var uiState by mutableStateOf(SearchUiState())
        private set

    private var searchJob: Job? = null

    init {
        matrixRepository?.let { loadSuggestions(it) }
    }

    private fun loadSuggestions(repository: MatrixRepository) {
        viewModelScope.launch {
            val nicknames = runCatching { repository.suggestedDmNicknames(8) }.getOrDefault(emptyList())
            // One search call per nickname -- there's no dedicated "look up by exact username"
            // endpoint on this server, only the same fuzzy accounts/search this screen already
            // uses, so that's reused here too. Run concurrently: up to 8 calls, same cost as one
            // typed search either way from the user's perspective.
            val resolved = nicknames.map { nickname ->
                async {
                    runCatching { profileRepository.search(nickname, resolve = false).getOrNull() }
                        .getOrNull()
                        ?.firstOrNull { it.username.equals(nickname, ignoreCase = true) }
                }
            }.awaitAll().filterNotNull()
            uiState = uiState.copy(suggestions = resolved)
        }
    }

    fun onQueryChange(value: String) {
        uiState = uiState.copy(query = value)
        searchJob?.cancel()
        if (value.isBlank()) {
            uiState = uiState.copy(results = emptyList(), isSearching = false, hasSearched = false)
            return
        }
        // Instant, zero-latency narrowing of the already-loaded suggestions while the debounced
        // network search below catches up -- avoids a blank list for the first keystroke.
        uiState = uiState.copy(results = uiState.suggestions.filter { it.matchesQuery(value) })
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
                // Frequent contacts that match surface first, ahead of the server's own order.
                val frequent = uiState.suggestions.filter { it.matchesQuery(query) }
                val merged = frequent + accounts.filter { account -> frequent.none { it.id == account.id } }
                uiState = uiState.copy(results = merged, isSearching = false, hasSearched = true)
                loadRelationships(merged.map { it.id })
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
        // A pending request (locked account, not yet approved) must unfollow to cancel it,
        // not follow again -- following==false is true in both the "not following" and
        // "requested" states, so requested has to be checked too.
        val newValue = current?.following != true && current?.requested != true
        // Optimistic update so the button changes immediately instead of only after the
        // round-trip. Locked accounts land in "requested", not "following", until approved.
        uiState = uiState.copy(
            relationships = uiState.relationships + (account.id to (current ?: Relationship(id = account.id)).copy(
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
