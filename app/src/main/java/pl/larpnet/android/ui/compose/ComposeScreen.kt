package pl.larpnet.android.ui.compose

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import pl.larpnet.android.R
import pl.larpnet.android.di.rememberAppContainer
import pl.larpnet.android.ui.common.visibilityLabel
import pl.larpnet.android.ui.common.visibilityOptions
import pl.larpnet.android.ui.theme.larpnetTopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(
    replyToId: String?,
    onBack: () -> Unit,
    onPosted: () -> Unit,
) {
    val appContainer = rememberAppContainer()
    val context = LocalContext.current
    val viewModel: ComposeViewModel = viewModel(
        key = "compose_${replyToId ?: "new"}",
        factory = viewModelFactory {
            initializer {
                ComposeViewModel(
                    replyToId,
                    appContainer.statusRepository,
                    appContainer.mediaRepository,
                    appContainer.profileRepository,
                    appContainer.recentTagsStore,
                )
            }
        },
    )
    val state = viewModel.uiState

    LaunchedEffect(state.posted) {
        if (state.posted) onPosted()
    }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(4),
    ) { uris -> uris.forEach { viewModel.addMedia(context, it) } }

    var visibilityMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = larpnetTopAppBarColors(),
                title = { Text(stringResource(if (replyToId != null) R.string.compose_title_reply else R.string.compose_title_new)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (state.isPosting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp).padding(end = 16.dp),
                        )
                    } else {
                        TextButton(
                            onClick = viewModel::publish,
                            enabled = state.canPublish,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = Color.White,
                                disabledContentColor = Color.White.copy(alpha = 0.4f),
                            ),
                        ) {
                            Text(stringResource(R.string.compose_publish))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val customAudienceCount = state.customAudienceCircles.size + state.customAudienceContacts.size
            val visibilityFieldValue = if (state.visibility == "custom" && customAudienceCount > 0) {
                "${visibilityLabel(state.visibility)} ($customAudienceCount)"
            } else {
                visibilityLabel(state.visibility)
            }
            ExposedDropdownMenuBox(
                expanded = visibilityMenuExpanded,
                onExpandedChange = { visibilityMenuExpanded = it },
            ) {
                OutlinedTextField(
                    value = visibilityFieldValue,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.compose_visibility_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = visibilityMenuExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = visibilityMenuExpanded,
                    onDismissRequest = { visibilityMenuExpanded = false },
                ) {
                    visibilityOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(visibilityLabel(option)) },
                            onClick = {
                                visibilityMenuExpanded = false
                                if (option == "custom") viewModel.openAudiencePicker() else viewModel.onVisibilityChange(option)
                            },
                        )
                    }
                }
            }

            if (state.showAudiencePicker) {
                AudiencePickerDialog(
                    circles = state.availableCircles,
                    followers = state.availableFollowers,
                    isLoading = state.isLoadingAudiencePicker,
                    selectedCircles = state.customAudienceCircles,
                    selectedContacts = state.customAudienceContacts,
                    onToggleCircle = viewModel::toggleCircle,
                    onToggleContact = viewModel::toggleContact,
                    onConfirm = viewModel::confirmAudience,
                    onDismiss = viewModel::dismissAudiencePicker,
                )
            }

            OutlinedTextField(
                value = state.spoilerText,
                onValueChange = viewModel::onSpoilerTextChange,
                label = { Text(stringResource(R.string.compose_spoiler_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.text,
                onValueChange = viewModel::onTextChange,
                placeholder = { Text(stringResource(R.string.compose_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            TagsSection(
                availableTags = state.toggleableTags,
                selectedTags = state.selectedTags,
                customTags = state.customTags,
                customTagInput = state.customTagInput,
                onToggleTag = viewModel::toggleTag,
                onCustomTagInputChange = viewModel::onCustomTagInputChange,
                onAddCustomTag = viewModel::addCustomTag,
                onRemoveCustomTag = viewModel::removeCustomTag,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // No `sensitive` param on the legacy endpoint custom-audience posts go through
                // -- see FriendicaApi.postStatusWithAudience's doc comment. Hidden rather than
                // silently ignored so it's not misleading.
                if (state.visibility != "custom") {
                    Checkbox(checked = state.sensitive, onCheckedChange = viewModel::onSensitiveChange)
                    Text(stringResource(R.string.compose_sensitive))
                }
                Box(modifier = Modifier.weight(1f))
                if (state.isUploadingMedia) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp).padding(end = 8.dp))
                }
                SmallFloatingActionButton(
                    onClick = { mediaPicker.launch(PickVisualMediaRequest()) },
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                }
            }

            if (state.mediaAttachments.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.mediaAttachments, key = { it.id }) { media ->
                        Box {
                            AsyncImage(
                                model = media.previewUrl ?: media.url,
                                contentDescription = media.description,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                            IconButton(
                                onClick = { viewModel.removeMedia(media.id) },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
