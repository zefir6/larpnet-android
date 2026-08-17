package pl.larpnet.android.ui.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pl.larpnet.android.R

/**
 * Tappable banner surfacing posts buffered by TimelineViewModel.startPolling() -- new content
 * never silently reflows the list under the reader, only appears once this is tapped.
 */
@Composable
fun NewPostsBanner(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = count > 0, modifier = modifier) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clickable(onClick = onClick),
        ) {
            Text(
                text = stringResource(R.string.timeline_new_posts, count),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            )
        }
    }
}
