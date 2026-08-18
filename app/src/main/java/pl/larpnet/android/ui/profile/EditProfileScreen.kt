package pl.larpnet.android.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import pl.larpnet.android.R
import pl.larpnet.android.data.repository.ProfileRepository
import pl.larpnet.android.di.rememberAppContainer
import pl.larpnet.android.ui.theme.larpnetTopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(onBack: () -> Unit, onSaved: () -> Unit) {
    val appContainer = rememberAppContainer()
    val viewModel: EditProfileViewModel = viewModel(
        factory = viewModelFactory {
            initializer { EditProfileViewModel(appContainer.profileRepository) }
        },
    )
    val state = viewModel.uiState

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = larpnetTopAppBarColors(),
                title = { Text(stringResource(R.string.edit_profile_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(
                        onClick = viewModel::save,
                        enabled = !state.isSaving && !state.isLoading,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color.White,
                            disabledContentColor = Color.White.copy(alpha = 0.4f),
                        ),
                    ) {
                        Text(stringResource(R.string.edit_profile_save))
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(padding))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = state.displayName,
                    onValueChange = viewModel::onDisplayNameChange,
                    label = { Text(stringResource(R.string.edit_profile_display_name)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                )
                OutlinedTextField(
                    value = state.note,
                    onValueChange = viewModel::onNoteChange,
                    label = { Text(stringResource(R.string.edit_profile_bio)) },
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                )

                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

private data class EditProfileUiState(
    val isLoading: Boolean = true,
    val displayName: String = "",
    val note: String = "",
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)

/**
 * [note] is edited as plain text, not raw HTML: the server stores/renders the bio as HTML
 * (same as post content), but there's no rich-text editor here for v1 -- we strip tags for
 * display and submit the plain text back, which Friendica will treat as a fresh plain bio.
 */
private class EditProfileViewModel(private val profileRepository: ProfileRepository) : ViewModel() {

    var uiState by mutableStateOf(EditProfileUiState())
        private set

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            profileRepository.me().fold(
                onSuccess = { account ->
                    uiState = uiState.copy(
                        isLoading = false,
                        displayName = account.displayName,
                        note = Jsoup.parse(account.note).text(),
                    )
                },
                onFailure = { e -> uiState = uiState.copy(isLoading = false, error = e.message) },
            )
        }
    }

    fun onDisplayNameChange(value: String) {
        uiState = uiState.copy(displayName = value)
    }

    fun onNoteChange(value: String) {
        uiState = uiState.copy(note = value)
    }

    fun save() {
        uiState = uiState.copy(isSaving = true, error = null)
        viewModelScope.launch {
            profileRepository.updateProfile(
                displayName = uiState.displayName,
                note = uiState.note,
            ).fold(
                onSuccess = { uiState = uiState.copy(isSaving = false, saved = true) },
                onFailure = { e -> uiState = uiState.copy(isSaving = false, error = e.message) },
            )
        }
    }
}
