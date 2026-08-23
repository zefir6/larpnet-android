package pl.larpnet.android.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import pl.larpnet.android.data.model.Circle
import pl.larpnet.android.data.model.FollowerEntry
import pl.larpnet.android.ui.common.AvatarImage

/**
 * Custom-audience picker for the compose screen -- circles ([circles]) plus a searchable list
 * of individual followers ([followers]), both multi-select. See ComposeViewModel's doc comment
 * for why [FollowerEntry.cid] (not [pl.larpnet.android.data.model.Account.id]) is what gets
 * collected here. Confirm is disabled on an empty selection: an empty audience means PUBLIC
 * server-side, not "nobody" -- see [onConfirm]'s caller.
 */
@Composable
fun AudiencePickerDialog(
    circles: List<Circle>,
    followers: List<FollowerEntry>,
    isLoading: Boolean,
    selectedCircles: Set<Int>,
    selectedContacts: Set<Int>,
    onToggleCircle: (Int) -> Unit,
    onToggleContact: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filteredFollowers = remember(followers, query) {
        if (query.isBlank()) {
            followers
        } else {
            followers.filter {
                it.name.contains(query, ignoreCase = true) || it.screenName.contains(query, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.audience_picker_title)) },
        text = {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Column(modifier = Modifier.heightIn(max = 480.dp)) {
                    Text(
                        stringResource(R.string.audience_picker_circles_section),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    if (circles.isEmpty()) {
                        Text(
                            stringResource(R.string.audience_picker_circles_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        circles.forEach { circle ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleCircle(circle.gid) },
                            ) {
                                Checkbox(
                                    checked = circle.gid in selectedCircles,
                                    onCheckedChange = { onToggleCircle(circle.gid) },
                                )
                                Text(circle.name)
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        stringResource(R.string.audience_picker_people_section),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(stringResource(R.string.audience_picker_search_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (filteredFollowers.isEmpty()) {
                        Text(
                            stringResource(R.string.audience_picker_people_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                            items(filteredFollowers, key = { it.cid }) { follower ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onToggleContact(follower.cid) }
                                        .padding(vertical = 4.dp),
                                ) {
                                    Checkbox(
                                        checked = follower.cid in selectedContacts,
                                        onCheckedChange = { onToggleContact(follower.cid) },
                                    )
                                    AvatarImage(
                                        url = follower.profileImageUrl,
                                        contentDescription = follower.name,
                                        size = 32.dp,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            follower.name.ifBlank { follower.screenName },
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        Text(
                                            "@${follower.screenName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = selectedCircles.isNotEmpty() || selectedContacts.isNotEmpty(),
            ) { Text(stringResource(R.string.audience_picker_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}
