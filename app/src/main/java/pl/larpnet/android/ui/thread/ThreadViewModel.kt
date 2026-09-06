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

data class ThreadRenderItem(
    val status: Status,
    val depth: Int,
    val hasChildren: Boolean,
    val isCollapsed: Boolean,
    val hiddenDescendantCount: Int,
)

data class ThreadUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val ancestors: List<Status> = emptyList(),
    val focus: Status? = null,
    val descendants: List<ThreadRenderItem> = emptyList(),
    val collapsedIds: Set<String> = emptySet(),
)

class ThreadViewModel(
    private val statusId: String,
    private val statusRepository: StatusRepository,
) : ViewModel() {

    var uiState by mutableStateOf(ThreadUiState())
        private set

    // The full reply tree, kept around so toggling collapse/expand can recompute the visible
    // (flattened) list without a re-fetch. Not part of uiState -- it's an intermediate structure,
    // not directly rendered -- but updateEverywhere must keep it in sync with uiState.descendants
    // (see updateTreeStatus) or a favourite/reblog/bookmark toggle would get silently reverted
    // the next time a sibling branch is collapsed or expanded.
    private var tree: ThreadNode? = null

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

            tree = buildThreadTree(focus, context.descendants)
            uiState = uiState.copy(
                isLoading = false,
                ancestors = context.ancestors,
                focus = focus,
                collapsedIds = emptySet(),
            )
            refreshDescendants()
        }
    }

    fun toggleCollapsed(statusId: String) {
        val collapsedIds = uiState.collapsedIds
        uiState = uiState.copy(
            collapsedIds = if (statusId in collapsedIds) collapsedIds - statusId else collapsedIds + statusId,
        )
        refreshDescendants()
    }

    private fun refreshDescendants() {
        val root = tree ?: return
        uiState = uiState.copy(
            descendants = root.children.flatMap { flattenNode(it, depth = 0, uiState.collapsedIds) },
        )
    }

    private fun flattenNode(node: ThreadNode, depth: Int, collapsedIds: Set<String>): List<ThreadRenderItem> {
        val isCollapsed = node.status.id in collapsedIds
        val item = ThreadRenderItem(
            status = node.status,
            depth = depth,
            hasChildren = node.children.isNotEmpty(),
            isCollapsed = isCollapsed,
            hiddenDescendantCount = if (isCollapsed) countDescendants(node) else 0,
        )
        return if (isCollapsed) {
            listOf(item)
        } else {
            listOf(item) + node.children.flatMap { flattenNode(it, depth + 1, collapsedIds) }
        }
    }

    private fun countDescendants(node: ThreadNode): Int =
        node.children.size + node.children.sumOf { countDescendants(it) }

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

    /** See TimelineViewModel.applyLocalUpdate's doc comment: for a boosted status, [id] is the
     * reblogged post's own id, not the top-level item's -- must check both. */
    private fun updateIfMatch(item: Status, id: String, transform: (Status) -> Status): Status = when {
        item.id == id -> transform(item)
        item.reblog?.id == id -> item.copy(reblog = transform(item.reblog))
        else -> item
    }

    private fun updateEverywhere(id: String, transform: (Status) -> Status) {
        tree = tree?.let { updateTreeStatus(it, id, transform) }
        uiState = uiState.copy(
            ancestors = uiState.ancestors.map { updateIfMatch(it, id, transform) },
            focus = uiState.focus?.let { updateIfMatch(it, id, transform) },
            descendants = uiState.descendants.map {
                it.copy(status = updateIfMatch(it.status, id, transform))
            },
        )
    }

    private fun updateTreeStatus(node: ThreadNode, id: String, transform: (Status) -> Status): ThreadNode =
        node.copy(
            status = updateIfMatch(node.status, id, transform),
            children = node.children.map { updateTreeStatus(it, id, transform) },
        )
}
