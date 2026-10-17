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
 * new-message and new-chat recipient pickers. [suggestFrequentContacts] additionally seeds the
 * screen with the most-recently-chatted-with contacts before any text is typed -- the new-chat
 * picker only (see `SearchViewModel`'s own doc comment); the new-message picker and plain
 * account-discovery search don't offer this.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onSelectAccount: ((Account) -> Unit)? = null,
    suggestFrequentContacts: Boolean = false,
) {
    val appContainer = rememberAppContainer()
    val viewModel: SearchViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                SearchViewModel(
                    appContainer.profileRepository,
                    matrixRepository = if (suggestFrequentContacts) appContainer.matrixRepository else null,
                )
            }
        },
    )
    val state = viewModel.uiState
    val focusRequester = remember { FocusRequester() }

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
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .focusRequester(focusRequester),
            )
            LaunchedEffect(Unit) { focusRequester.requestFocus() }

            when {
                state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.error, color = MaterialTheme.colorScheme.error)
                }

                // Shown before any typing -- the most-recently-chatted-with contacts, so the
                // picker isn't just a blank box waiting for a keystroke.
                state.query.isBlank() && state.suggestions.isNotEmpty() -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Text(
                            stringResource(R.string.search_suggested),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(state.suggestions, key = { it.id }) { account ->
                        AccountRow(
                            account = account,
                            relationship = state.relationships[account.id],
                            onClick = { if (onSelectAccount != null) onSelectAccount(account) else onOpenProfile(account.id) },
                            onToggleFollow = { viewModel.toggleFollow(account) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }

                // Only a full-screen spinner when there's nothing to show yet -- once instant
                // suggestion-matches or a previous result set is on screen, keep showing it while
                // the debounced network search refines it, instead of blanking the list.
                state.isSearching && state.results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.hasSearched && state.results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.search_empty))
                }

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.results, key = { it.id }) { account ->
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
