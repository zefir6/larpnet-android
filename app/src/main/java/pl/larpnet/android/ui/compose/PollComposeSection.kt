package pl.larpnet.android.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.larpnet.android.R
import pl.larpnet.android.ui.common.pollExpiryLabel

/** Local-only poll creation fields, shown in [ComposeScreen] when [ComposeUiState.pollEnabled]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PollComposeSection(
    options: List<String>,
    multiple: Boolean,
    expiresInSeconds: Int,
    onOptionChange: (Int, String) -> Unit,
    onAddOption: () -> Unit,
    onRemoveOption: (Int) -> Unit,
    onMultipleChange: (Boolean) -> Unit,
    onExpiresInChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, option ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = option,
                    onValueChange = { onOptionChange(index, it) },
                    placeholder = { Text(stringResource(R.string.compose_poll_option_hint, index + 1)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                if (options.size > MIN_POLL_OPTIONS) {
                    IconButton(onClick = { onRemoveOption(index) }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.compose_poll_remove_option_cd))
                    }
                }
            }
        }

        if (options.size < MAX_POLL_OPTIONS) {
            TextButton(onClick = onAddOption) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.compose_poll_add_option))
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = multiple, onCheckedChange = onMultipleChange)
            Text(stringResource(R.string.compose_poll_multiple))
        }

        var expiryMenuExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expiryMenuExpanded,
            onExpandedChange = { expiryMenuExpanded = it },
        ) {
            OutlinedTextField(
                value = pollExpiryLabel(expiresInSeconds),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.compose_poll_expiry_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expiryMenuExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = expiryMenuExpanded,
                onDismissRequest = { expiryMenuExpanded = false },
            ) {
                POLL_EXPIRY_CHOICES.forEach { seconds ->
                    DropdownMenuItem(
                        text = { Text(pollExpiryLabel(seconds)) },
                        onClick = {
                            expiryMenuExpanded = false
                            onExpiresInChange(seconds)
                        },
                    )
                }
            }
        }
    }
}
