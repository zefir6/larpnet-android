package pl.larpnet.android.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.larpnet.android.R
import pl.larpnet.android.data.model.Poll

/**
 * Renders a poll's options -- a vote form when the current user hasn't voted yet and the poll
 * hasn't expired, or read-only result bars otherwise. Local-only (see [Poll]'s doc comment):
 * [onVote] always votes against this instance's own tally.
 */
@Composable
fun PollView(poll: Poll, onVote: (List<Int>) -> Unit, modifier: Modifier = Modifier) {
    val showResults = poll.voted || poll.expired
    var selectedSingle by remember(poll.id) { mutableStateOf<Int?>(null) }
    var selectedMultiple by remember(poll.id) { mutableStateOf(setOf<Int>()) }

    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp)) {
        poll.options.forEachIndexed { index, option ->
            if (showResults) {
                val percent = if (poll.votesCount > 0) {
                    ((option.votesCount ?: 0) * 100 / poll.votesCount).toInt()
                } else {
                    0
                }
                val ownVote = poll.ownVotes?.contains(index) == true
                Column(modifier = Modifier.padding(vertical = 2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = option.title,
                            style = if (ownVote) {
                                MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                        )
                        Text("$percent%", style = MaterialTheme.typography.labelSmall)
                    }
                    LinearProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (poll.multiple) {
                        Checkbox(
                            checked = index in selectedMultiple,
                            onCheckedChange = { checked ->
                                selectedMultiple = if (checked) selectedMultiple + index else selectedMultiple - index
                            },
                        )
                    } else {
                        RadioButton(selected = selectedSingle == index, onClick = { selectedSingle = index })
                    }
                    Text(option.title)
                }
            }
        }

        if (showResults) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.poll_votes_count, poll.votesCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (poll.expired) {
                    Text(
                        text = " · " + stringResource(R.string.poll_expired),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            val choices = if (poll.multiple) selectedMultiple.toList() else listOfNotNull(selectedSingle)
            TextButton(onClick = { onVote(choices) }, enabled = choices.isNotEmpty()) {
                Text(stringResource(R.string.poll_vote))
            }
        }
    }
}
