package pl.larpnet.android.ui.messages

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pl.larpnet.android.data.model.Conversation
import pl.larpnet.android.data.repository.ConversationRepository

data class ConversationsUiState(
    val items: List<Conversation> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val canLoadMore: Boolean = true,
)

class ConversationsViewModel(private val repository: ConversationRepository) : ViewModel() {

    var uiState by mutableStateOf(ConversationsUiState())
        private set

    private var nextMaxId: String? = null

    init {
        loadInitial()
    }

    fun loadInitial() {
        uiState = uiState.copy(isLoading = true, error = null)
        viewModelScope.launch {
            repository.list(maxId = null).fold(
                onSuccess = { page ->
                    nextMaxId = page.nextMaxId
                    uiState = uiState.copy(items = page.items, isLoading = false, canLoadMore = page.nextMaxId != null)
                },
                onFailure = { e -> uiState = uiState.copy(isLoading = false, error = e.message) },
            )
        }
    }

    fun loadMore() {
        if (uiState.isLoading || !uiState.canLoadMore) return
        val cursor = nextMaxId ?: return
        viewModelScope.launch {
            repository.list(maxId = cursor).fold(
                onSuccess = { page ->
                    nextMaxId = page.nextMaxId
                    uiState = uiState.copy(items = uiState.items + page.items, canLoadMore = page.nextMaxId != null)
                },
                onFailure = { e -> uiState = uiState.copy(error = e.message) },
            )
        }
    }

    fun refresh() {
        uiState = uiState.copy(isRefreshing = true)
        viewModelScope.launch {
            repository.list(maxId = null).fold(
                onSuccess = { page ->
                    nextMaxId = page.nextMaxId
                    uiState = uiState.copy(items = page.items, isRefreshing = false, canLoadMore = page.nextMaxId != null)
                },
                onFailure = { e -> uiState = uiState.copy(isRefreshing = false, error = e.message) },
            )
        }
    }

    fun delete(conversation: Conversation) {
        uiState = uiState.copy(items = uiState.items.filterNot { it.id == conversation.id })
        viewModelScope.launch { repository.delete(conversation.id) }
    }
}
