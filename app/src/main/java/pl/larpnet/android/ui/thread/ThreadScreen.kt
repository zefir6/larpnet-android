package pl.larpnet.android.ui.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import pl.larpnet.android.R
import pl.larpnet.android.data.model.Status
import pl.larpnet.android.di.rememberAppContainer
import pl.larpnet.android.ui.theme.larpnetTopAppBarColors
import pl.larpnet.android.ui.timeline.StatusCard

// Beyond this many levels, further nesting stops indenting further -- otherwise a long reply
// chain squeezes the card down to nothing.
private const val MAX_INDENT_DEPTH = 6

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(
    statusId: String,
    onBack: () -> Unit,
    onOpenThread: (Status) -> Unit,
    onOpenProfile: (String) -> Unit,
    onReply: (Status) -> Unit,
) {
    val appContainer = rememberAppContainer()
    val viewModel: ThreadViewModel = viewModel(
        key = "thread_$statusId",
        factory = viewModelFactory {
            initializer { ThreadViewModel(statusId, appContainer.statusRepository) }
        },
    )
    val state = viewModel.uiState

    // Opening a thread on a reply deep in a conversation otherwise leaves the view at the very
    // top of the ancestor chain -- the focused post (always right after the ancestors, so its
    // index is exactly ancestors.size) ends up below the fold, looking like the app reopened the
    // root post instead of jumping to the one that was tapped. No-op for a thread with no
    // ancestors (focus is already at index 0, already on-screen).
    // Keyed on isLoading (not focus/ancestors themselves) so this fires once when the thread
    // finishes its initial load, not again on every unrelated recomposition afterward --
    // favourite/reblog/bookmark toggles replace the focus/ancestor Status objects too (see
    // ThreadViewModel.updateEverywhere), which would otherwise yank the scroll position back
    // to the focus item on every tap elsewhere in the thread.
    val listState = rememberLazyListState()
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading && state.focus != null && state.ancestors.isNotEmpty()) {
            listState.scrollToItem(state.ancestors.size)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = larpnetTopAppBarColors(),
                title = { Text(stringResource(R.string.thread_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            state.error != null && state.focus == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(state.error)
            }

            else -> LazyColumn(state = listState, modifier = Modifier.padding(padding)) {
                items(state.ancestors, key = { "a_${it.id}" }) { status ->
                    StatusCard(
                        status = status,
                        onOpenThread = onOpenThread,
                        onOpenProfile = onOpenProfile,
                        onReply = onReply,
                        onToggleFavourite = viewModel::toggleFavourite,
                        onToggleReblog = viewModel::toggleReblog,
                        onToggleBookmark = viewModel::toggleBookmark,
                    )
                }

                state.focus?.let { focus ->
                    item(key = "focus_${focus.id}") {
                        Box(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
                            StatusCard(
                                status = focus,
                                onOpenThread = {},
                                onOpenProfile = onOpenProfile,
                                onReply = onReply,
                                onToggleFavourite = viewModel::toggleFavourite,
                                onToggleReblog = viewModel::toggleReblog,
                                onToggleBookmark = viewModel::toggleBookmark,
                            )
                        }
                    }
                }

                items(state.descendants, key = { "d_${it.status.id}" }) { renderItem ->
                    // Deep replies would otherwise squeeze the card down to nothing, so indent
                    // stops growing past MAX_INDENT_DEPTH levels -- further nesting stays flat.
                    val indentDepth = renderItem.depth.coerceAtMost(MAX_INDENT_DEPTH)
                    Column(modifier = Modifier.animateItem()) {
                        Row(verticalAlignment = Alignment.Top) {
                            if (indentDepth > 0) {
                                Box(modifier = Modifier.width((indentDepth * 16).dp))
                            }
                            if (renderItem.hasChildren) {
                                IconButton(
                                    onClick = { viewModel.toggleCollapsed(renderItem.status.id) },
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(
                                        if (renderItem.isCollapsed) Icons.Filled.KeyboardArrowRight else Icons.Filled.KeyboardArrowDown,
                                        contentDescription = stringResource(
                                            if (renderItem.isCollapsed) R.string.thread_expand_replies else R.string.thread_collapse_replies,
                                        ),
                                    )
                                }
                            }
                            StatusCard(
                                status = renderItem.status,
                                onOpenThread = onOpenThread,
                                onOpenProfile = onOpenProfile,
                                onReply = onReply,
                                onToggleFavourite = viewModel::toggleFavourite,
                                onToggleReblog = viewModel::toggleReblog,
                                onToggleBookmark = viewModel::toggleBookmark,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (renderItem.isCollapsed) {
                            Text(
                                text = stringResource(R.string.thread_hidden_replies, renderItem.hiddenDescendantCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(start = (indentDepth * 16 + 40).dp, bottom = 8.dp)
                                    .clickable { viewModel.toggleCollapsed(renderItem.status.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}
