package pl.larpnet.android.ui.thread

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pl.larpnet.android.data.model.Status
import pl.larpnet.android.data.repository.StatusRepository
import pl.larpnet.android.domain.thread.ThreadNode
import pl.larpnet.android.domain.thread.buildThreadTree

data class ThreadRenderItem(val status: Status, val depth: Int)

data class ThreadUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val ancestors: List<Status> = emptyList(),
    val focus: Status? = null,
    val descendants: List<ThreadRenderItem> = emptyList(),
)

class ThreadViewModel(
    private val statusId: String,
    private val statusRepository: StatusRepository,
) : ViewModel() {

    var uiState by mutableStateOf(ThreadUiState())
        private set

    init {
        load()
    }

    fun load() {
        uiState = uiState.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val statusResult = statusRepository.get(statusId)
            val focus = statusResult.getOrElse {
                uiState = uiState.copy(isLoading = false, error = it.message)
                return@launch
            }

            val contextResult = statusRepository.context(statusId)
            val context = contextResult.getOrElse {
                uiState = uiState.copy(isLoading = false, error = it.message, focus = focus)
                return@launch
            }

            val tree = buildThreadTree(focus, context.descendants)
            uiState = uiState.copy(
                isLoading = false,
                ancestors = context.ancestors,
                focus = focus,
                descendants = tree.children.flatMap { flattenNode(it, depth = 0) },
            )
        }
    }

    private fun flattenNode(node: ThreadNode, depth: Int): List<ThreadRenderItem> =
        listOf(ThreadRenderItem(node.status, depth)) + node.children.flatMap { flattenNode(it, depth + 1) }

    fun toggleFavourite(status: Status) {
        val newValue = !status.favourited
        updateEverywhere(status.id) {
            it.copy(favourited = newValue, favouritesCount = it.favouritesCount + if (newValue) 1 else -1)
        }
        viewModelScope.launch {
            statusRepository.setFavourited(status.id, newValue).onSuccess { updated -> updateEverywhere(updated.id) { updated } }
        }
    }

    fun toggleReblog(status: Status) {
        val newValue = !status.reblogged
        updateEverywhere(status.id) {
            it.copy(reblogged = newValue, reblogsCount = it.reblogsCount + if (newValue) 1 else -1)
        }
        viewModelScope.launch {
            statusRepository.setReblogged(status.id, newValue).onSuccess { updated -> updateEverywhere(updated.id) { updated } }
        }
    }

    fun toggleBookmark(status: Status) {
        val newValue = !status.bookmarked
        updateEverywhere(status.id) { it.copy(bookmarked = newValue) }
        viewModelScope.launch {
            statusRepository.setBookmarked(status.id, newValue).onSuccess { updated -> updateEverywhere(updated.id) { updated } }
        }
    }

    private fun updateEverywhere(id: String, transform: (Status) -> Status) {
        uiState = uiState.copy(
            ancestors = uiState.ancestors.map { if (it.id == id) transform(it) else it },
            focus = uiState.focus?.let { if (it.id == id) transform(it) else it },
            descendants = uiState.descendants.map {
                if (it.status.id == id) it.copy(status = transform(it.status)) else it
            },
        )
    }
}
