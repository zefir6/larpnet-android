package pl.larpnet.android.ui.common

import android.text.format.DateUtils
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import kotlinx.datetime.Instant

/**
 * Locale-aware relative timestamp ("5 min. temu" / "5 min ago" depending on device locale),
 * delegating to the platform's DateUtils rather than hand-rolling pl/en strings.
 */
@Composable
fun RelativeTime(
    instant: Instant,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelSmall,
) {
    val text = remember(instant) {
        DateUtils.getRelativeTimeSpanString(
            instant.toEpochMilliseconds(),
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
    }
    Text(text = text, modifier = modifier, style = style, color = LocalContentColor.current)
}
