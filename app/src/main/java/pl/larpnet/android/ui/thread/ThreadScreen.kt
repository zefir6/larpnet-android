package pl.larpnet.android.ui.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import pl.larpnet.android.ui.timeline.StatusCard

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

    Scaffold(
        topBar = {
            TopAppBar(
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

            else -> LazyColumn(modifier = Modifier.padding(padding)) {
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
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }

                items(state.descendants, key = { "d_${it.status.id}" }) { renderItem ->
                    Row {
                        if (renderItem.depth > 0) {
                            Box(modifier = Modifier.width((renderItem.depth * 16).dp))
                        }
                        StatusCard(
                            status = renderItem.status,
                            onOpenThread = onOpenThread,
                            onOpenProfile = onOpenProfile,
                            onReply = onReply,
                            onToggleFavourite = viewModel::toggleFavourite,
                            onToggleReblog = viewModel::toggleReblog,
                            onToggleBookmark = viewModel::toggleBookmark,
                            modifier = Modifier,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
