package pl.larpnet.android.ui.moderation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import pl.larpnet.android.R
import pl.larpnet.android.data.model.Account
import pl.larpnet.android.di.rememberAppContainer
import pl.larpnet.android.ui.common.AvatarImage
import pl.larpnet.android.ui.theme.larpnetTopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedAccountsScreen(onBack: () -> Unit) {
    val appContainer = rememberAppContainer()
    val viewModel: BlockedAccountsViewModel = viewModel(
        factory = viewModelFactory { initializer { BlockedAccountsViewModel(appContainer.profileRepository) } },
    )
    val state = viewModel.uiState
    val listState = rememberLazyListState()

    LaunchedEffect(listState, state.accounts.size, state.canLoadMore) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                if (last != null && state.accounts.isNotEmpty() && last >= state.accounts.size - 3) {
                    viewModel.loadMore()
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = larpnetTopAppBarColors(),
                title = { Text(stringResource(R.string.blocked_accounts_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading && state.accounts.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            state.error != null && state.accounts.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error, color = MaterialTheme.colorScheme.error)
                    Button(onClick = viewModel::loadInitial, modifier = Modifier.padding(top = 8.dp)) {
                        Text(stringResource(R.string.timeline_error_retry))
                    }
                }
            }

            state.accounts.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.blocked_accounts_empty))
            }

            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding)) {
                items(state.accounts, key = { it.id }) { account ->
                    BlockedAccountRow(account = account, onUnblock = { viewModel.unblock(account) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                if (state.isLoadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockedAccountRow(account: Account, onUnblock: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarImage(url = account.avatar, contentDescription = account.displayName)
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(text = account.displayName.ifBlank { account.username }, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "@${account.acct}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onUnblock) { Text(stringResource(R.string.blocked_accounts_unblock)) }
    }
}
