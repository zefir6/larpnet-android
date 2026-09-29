package pl.larpnet.android.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import pl.larpnet.android.R
import pl.larpnet.android.data.model.Account
import pl.larpnet.android.di.rememberAppContainer
import pl.larpnet.android.ui.common.AccountRow
import pl.larpnet.android.ui.theme.larpnetTopAppBarColors

/**
 * [onSelectAccount], when set, replaces the default "tap opens profile" behavior -- used by the
 * new-message recipient picker.
 *
 * [chatRecipientMode] scopes this to picking a Matrix chat partner rather than a general
 * profile lookup: `accounts/search` searches the whole known fediverse by default, but Matrix
 * identities only exist for *local* Larpnet accounts (see
 * `MatrixRepository.openOrCreateDirectRoom`), so a remote result here could never actually
 * start a chat -- filtered client-side (no `local`-only param exists on the search endpoint,
 * unlike `directory`) by the Mastodon-API convention that a local account's `acct` has no
 * `@domain` suffix. Also offers a direct "enter a Matrix address" fallback via
 * [onEnterMatrixAddress] for someone who already knows the exact address (including a
 * federated one on a different homeserver, which the directory search couldn't find anyway).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onSelectAccount: ((Account) -> Unit)? = null,
    chatRecipientMode: Boolean = false,
    onEnterMatrixAddress: ((String) -> Unit)? = null,
) {
    val appContainer = rememberAppContainer()
    val viewModel: SearchViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SearchViewModel(appContainer.profileRepository) }
        },
    )
    val state = viewModel.uiState
    val displayedResults = if (chatRecipientMode) state.results.filter { !it.acct.contains("@") } else state.results
    val focusRequester = remember { FocusRequester() }
    var customAddress by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = larpnetTopAppBarColors(),
                title = { Text(stringResource(R.string.search_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = {
                    Text(
                        if (chatRecipientMode) {
                            stringResource(R.string.search_hint_larpnet_users)
                        } else {
                            stringResource(R.string.search_hint)
                        },
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .focusRequester(focusRequester),
            )
            LaunchedEffect(Unit) { focusRequester.requestFocus() }

            if (chatRecipientMode) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.search_matrix_address_label), style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(
                        value = customAddress,
                        onValueChange = { customAddress = it },
                        placeholder = { Text("@user:server") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                    Button(
                        onClick = { onEnterMatrixAddress?.invoke(customAddress.trim()) },
                        enabled = customAddress.isNotBlank(),
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text(stringResource(R.string.search_matrix_address_start)) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            when {
                state.isSearching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.error, color = MaterialTheme.colorScheme.error)
                }

                state.hasSearched && displayedResults.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.search_empty))
                }

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(displayedResults, key = { it.id }) { account ->
                        AccountRow(
                            account = account,
                            relationship = state.relationships[account.id],
                            onClick = { if (onSelectAccount != null) onSelectAccount(account) else onOpenProfile(account.id) },
                            onToggleFollow = { viewModel.toggleFollow(account) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}
