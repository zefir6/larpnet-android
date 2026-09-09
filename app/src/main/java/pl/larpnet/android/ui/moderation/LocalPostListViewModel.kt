package pl.larpnet.android.ui.moderation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pl.larpnet.android.data.model.Status
import pl.larpnet.android.data.repository.StatusRepository
import pl.larpnet.android.network.NetworkError

/** One entry in the list -- [status] is null when the post 404s (deleted, or the author removed
 * it), shown as an "unavailable" placeholder rather than silently dropped from the list (the
 * user might still want to un-hide/un-block the id even if it can no longer be displayed). */
data class LocalPostEntry(val id: String, val status: Status?, val unavailable: Boolean = false)

data class LocalPostListUiState(
    val entries: List<LocalPostEntry> = emptyList(),
    val isLoading: Boolean = true,
)

/** Reusable for both the hidden-posts and blocked-posts screens -- parameterized by which
 * [LocalPostFilterStore] it reads ids from. */
class LocalPostListViewModel(
    private val store: LocalPostFilterStore,
    private val statusRepository: StatusRepository,
) : ViewModel() {

    var uiState by mutableStateOf(LocalPostListUiState())
        private set

    init {
        viewModelScope.launch {
            store.ids.collect { ids -> load(ids) }
        }
    }

    private suspend fun load(ids: List<String>) {
        uiState = uiState.copy(isLoading = true)
        val entries = ids.map { id ->
            statusRepository.get(id).fold(
                onSuccess = { LocalPostEntry(id, it) },
                onFailure = { e ->
                    val unavailable = e is NetworkError.Http && e.status == 404
                    LocalPostEntry(id, null, unavailable = unavailable)
                },
            )
        }
        uiState = uiState.copy(entries = entries, isLoading = false)
    }

    fun remove(id: String) = store.remove(id)
}
