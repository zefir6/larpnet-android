package pl.larpnet.android.ui.notifications

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pl.larpnet.android.data.model.Notification
import pl.larpnet.android.data.repository.NotificationRepository

data class NotificationsUiState(
    val items: List<Notification> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val canLoadMore: Boolean = true,
)

class NotificationsViewModel(private val repository: NotificationRepository) : ViewModel() {

    var uiState by mutableStateOf(NotificationsUiState())
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
        if (uiState.isLoadingMore || !uiState.canLoadMore) return
        val cursor = nextMaxId ?: return
        uiState = uiState.copy(isLoadingMore = true)
        viewModelScope.launch {
            repository.list(maxId = cursor).fold(
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
            repository.list(maxId = null).fold(
                onSuccess = { page ->
                    nextMaxId = page.nextMaxId
                    uiState = uiState.copy(items = page.items, isRefreshing = false, canLoadMore = page.nextMaxId != null)
                },
                onFailure = { e -> uiState = uiState.copy(isRefreshing = false, error = e.message) },
            )
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clearAll().onSuccess {
                uiState = uiState.copy(items = emptyList(), canLoadMore = false)
            }
        }
    }

    fun dismiss(id: String) {
        uiState = uiState.copy(items = uiState.items.filterNot { it.id == id })
        viewModelScope.launch { repository.dismiss(id) }
    }
}
