package pl.larpnet.android.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import pl.larpnet.android.ui.common.AvatarCropDialog
import pl.larpnet.android.ui.common.AvatarImage
import pl.larpnet.android.ui.theme.larpnetTopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(onBack: () -> Unit, onSaved: () -> Unit) {
    val appContainer = rememberAppContainer()
    val viewModel: EditProfileViewModel = viewModel(
        factory = viewModelFactory {
            initializer { EditProfileViewModel(appContainer.profileRepository, appContainer::bumpAvatarVersion) }
        },
    )
    val state = viewModel.uiState

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(viewModel::stageAvatarForCropping) }

    state.pendingCropUri?.let { uri ->
        AvatarCropDialog(
            imageUri = uri,
            onCrop = viewModel::uploadAvatar,
            onDismiss = viewModel::cancelCrop,
        )
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
                        enabled = !state.isSaving && !state.isLoading && !state.isUploadingAvatar,
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
                Box(
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .clickable(enabled = !state.isUploadingAvatar) { avatarPicker.launch(PickVisualMediaRequest()) },
                    contentAlignment = Alignment.Center,
                ) {
                    AvatarImage(
                        url = state.avatarUrlForDisplay,
                        contentDescription = state.displayName,
                        size = 96.dp,
                    )
                    if (state.isUploadingAvatar) {
                        Box(
                            modifier = Modifier.size(96.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                        }
                    } else {
                        Icon(
                            Icons.Filled.CameraAlt,
                            contentDescription = stringResource(R.string.edit_profile_change_avatar),
                            tint = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(6.dp),
                        )
                    }
                }
                state.avatarError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                OutlinedTextField(
                    value = state.displayName,
                    onValueChange = viewModel::onDisplayNameChange,
                    label = { Text(stringResource(R.string.edit_profile_display_name)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 12.dp),
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
    val avatarUrl: String = "",
    val pendingCropUri: Uri? = null,
    val isUploadingAvatar: Boolean = false,
    val avatarError: String? = null,
    val avatarCacheBust: String = "",
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
) {
    val avatarUrlForDisplay: String
        get() = if (avatarCacheBust.isBlank() || avatarUrl.isBlank()) {
            avatarUrl
        } else {
            "$avatarUrl${if ("?" in avatarUrl) "&" else "?"}$avatarCacheBust"
        }
}

/**
 * [note] is edited as plain text, not raw HTML: the server stores/renders the bio as HTML
 * (same as post content), but there's no rich-text editor here for v1 -- we strip tags for
 * display and submit the plain text back, which Friendica will treat as a fresh plain bio.
 */
private class EditProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val onAvatarChanged: () -> Unit,
) : ViewModel() {

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
                        avatarUrl = account.avatar,
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

    fun stageAvatarForCropping(uri: Uri) {
        uiState = uiState.copy(pendingCropUri = uri, avatarError = null)
    }

    fun cancelCrop() {
        uiState = uiState.copy(pendingCropUri = null)
    }

    fun uploadAvatar(croppedJpegBytes: ByteArray) {
        uiState = uiState.copy(pendingCropUri = null, isUploadingAvatar = true, avatarError = null)
        viewModelScope.launch {
            profileRepository.uploadAvatar(croppedJpegBytes, "image/jpeg", "avatar.jpg").fold(
                onSuccess = { account ->
                    onAvatarChanged()
                    uiState = uiState.copy(
                        isUploadingAvatar = false,
                        avatarUrl = account.avatar,
                        avatarCacheBust = "_v=${System.currentTimeMillis()}",
                    )
                },
                onFailure = { e -> uiState = uiState.copy(isUploadingAvatar = false, avatarError = e.message) },
            )
        }
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
