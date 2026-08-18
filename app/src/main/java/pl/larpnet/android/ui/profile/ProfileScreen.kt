package pl.larpnet.android.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import pl.larpnet.android.R
import pl.larpnet.android.data.model.Status
import pl.larpnet.android.di.rememberAppContainer
import pl.larpnet.android.domain.html.HtmlParser
import pl.larpnet.android.ui.common.AvatarImage
import pl.larpnet.android.ui.common.HtmlContent
import pl.larpnet.android.ui.theme.larpnetTopAppBarColors
import pl.larpnet.android.ui.timeline.StatusCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    accountId: String?,
    onBack: (() -> Unit)? = null,
    onOpenThread: (Status) -> Unit,
    onOpenProfile: (String) -> Unit,
    onReply: (Status) -> Unit,
    onEditProfile: () -> Unit,
    onSearch: () -> Unit = {},
) {
    val appContainer = rememberAppContainer()
    val viewModel: ProfileViewModel = viewModel(
        key = "profile_${accountId ?: "me"}",
        factory = viewModelFactory {
            initializer {
                ProfileViewModel(
                    accountId,
                    appContainer.profileRepository,
                    appContainer.timelineRepository,
                    appContainer.statusRepository,
                    appContainer.authRepository,
                )
            }
        },
    )
    val state = viewModel.uiState
    val listState = rememberLazyListState()

    LaunchedEffect(listState, state.statuses.size, state.canLoadMore) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                if (last != null && state.statuses.isNotEmpty() && last >= state.statuses.size - 3) {
                    viewModel.loadMore()
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = larpnetTopAppBarColors(),
                title = { Text(stringResource(R.string.nav_profile)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search_title))
                    }
                    if (state.isOwn) {
                        IconButton(onClick = onEditProfile) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.profile_edit))
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading && state.account == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            state.account == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(state.error.orEmpty())
            }

            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding)) {
                item {
                    ProfileHeader(state = state, onToggleFollow = viewModel::toggleFollow)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                items(state.statuses, key = { it.id }) { status ->
                    StatusCard(
                        status = status,
                        onOpenThread = onOpenThread,
                        onOpenProfile = onOpenProfile,
                        onReply = onReply,
                        onToggleFavourite = viewModel::toggleFavourite,
                        onToggleReblog = viewModel::toggleReblog,
                        onToggleBookmark = viewModel::toggleBookmark,
                        onDelete = if (state.isOwn) viewModel::deleteStatus else null,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(state: ProfileUiState, onToggleFollow: () -> Unit) {
    val account = state.account ?: return
    val bioNodes = remember(account.note) { HtmlParser.parse(account.note) }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        AvatarImage(url = account.avatar, contentDescription = account.displayName, size = 72.dp)
        Text(
            text = account.displayName.ifBlank { account.username },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = "@${account.acct}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (account.note.isNotBlank()) {
            HtmlContent(bioNodes, modifier = Modifier.padding(top = 8.dp))
        }

        Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ProfileStat(account.statusesCount, stringResource(R.string.profile_posts))
            ProfileStat(account.followersCount, stringResource(R.string.profile_followers))
            ProfileStat(account.followingCount, stringResource(R.string.profile_following))
        }

        if (!state.isOwn) {
            val isFollowing = state.relationship?.following == true
            val label = stringResource(if (isFollowing) R.string.profile_unfollow else R.string.profile_follow)
            Box(modifier = Modifier.padding(top = 12.dp)) {
                if (isFollowing) {
                    OutlinedButton(onClick = onToggleFollow) { Text(label) }
                } else {
                    Button(onClick = onToggleFollow) { Text(label) }
                }
            }
        }
    }
}

@Composable
private fun ProfileStat(count: Long, label: String) {
    Column {
        Text(text = count.toString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
