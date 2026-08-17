package pl.larpnet.android.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.larpnet.android.R
import pl.larpnet.android.data.model.Account
import pl.larpnet.android.data.model.Relationship

@Composable
fun AccountRow(
    account: Account,
    relationship: Relationship?,
    onClick: () -> Unit,
    onToggleFollow: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
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
        if (relationship != null) {
            val label = stringResource(if (relationship.following) R.string.profile_unfollow else R.string.profile_follow)
            if (relationship.following) {
                OutlinedButton(onClick = onToggleFollow) { Text(label) }
            } else {
                Button(onClick = onToggleFollow) { Text(label) }
            }
        }
    }
}
