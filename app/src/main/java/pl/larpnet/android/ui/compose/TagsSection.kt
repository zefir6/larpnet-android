package pl.larpnet.android.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import pl.larpnet.android.R
import pl.larpnet.android.ui.theme.LarpnetMutedText
import pl.larpnet.android.ui.theme.larpnetCard
import pl.larpnet.android.ui.theme.larpnetChipColors

/**
 * Toggleable predefined/recent tag chips plus a freetext entry for custom tags, shown below the
 * main body field in [ComposeScreen]. Selected/added tags are folded into the outgoing status
 * text at publish time -- see [ComposeViewModel.publish].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TagsSection(
    availableTags: List<String>,
    selectedTags: Set<String>,
    customTags: List<String>,
    customTagInput: String,
    onToggleTag: (String) -> Unit,
    onCustomTagInputChange: (String) -> Unit,
    onAddCustomTag: () -> Unit,
    onRemoveCustomTag: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .larpnetCard()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.compose_tags_label),
            style = MaterialTheme.typography.labelMedium,
            color = LarpnetMutedText,
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            availableTags.forEach { tag ->
                FilterChip(
                    selected = tag in selectedTags,
                    onClick = { onToggleTag(tag) },
                    label = { Text("#$tag") },
                    colors = larpnetChipColors(),
                )
            }
            customTags.forEach { tag ->
                FilterChip(
                    selected = true,
                    onClick = { onRemoveCustomTag(tag) },
                    label = { Text("#$tag") },
                    trailingIcon = {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.compose_tags_remove_cd),
                        )
                    },
                    colors = larpnetChipColors(),
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = customTagInput,
                onValueChange = onCustomTagInputChange,
                placeholder = { Text(stringResource(R.string.compose_tags_add_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAddCustomTag() }),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onAddCustomTag, enabled = customTagInput.isNotBlank()) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.compose_tags_add_cd))
            }
        }
    }
}
