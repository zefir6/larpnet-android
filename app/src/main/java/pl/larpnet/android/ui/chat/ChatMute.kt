package pl.larpnet.android.ui.chat

import androidx.annotation.StringRes
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import pl.larpnet.android.R
import java.text.DateFormat
import java.util.Date

/** Mute durations offered everywhere (same list as the web client's `MUTE_OPTIONS`); null =
 * until turned back on. */
internal val muteDurations: List<Pair<Int, Long?>> = listOf(
    R.string.chat_mute_1h to 3_600_000L,
    R.string.chat_mute_8h to 8 * 3_600_000L,
    R.string.chat_mute_24h to 24 * 3_600_000L,
    R.string.chat_mute_week to 7 * 24 * 3_600_000L,
    R.string.chat_mute_forever to null,
)

/** Picker for [muteDurations]; [onPick] gets the duration in millis, or null for "until turned
 * back on". With [onUnmute] set (the room is already muted) it offers only that instead. */
@Composable
internal fun MuteDurationMenu(expanded: Boolean, onDismiss: () -> Unit, onPick: (Long?) -> Unit, onUnmute: (() -> Unit)? = null) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (onUnmute != null) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_unmute)) },
                onClick = {
                    onDismiss()
                    onUnmute()
                },
            )
            return@DropdownMenu
        }
        muteDurations.forEach { (@StringRes label, duration) ->
            DropdownMenuItem(
                text = { Text(stringResource(label)) },
                onClick = {
                    onDismiss()
                    onPick(duration)
                },
            )
        }
    }
}

/** "Muted" / "Muted until <date time>" label. */
@Composable
internal fun muteLabel(mutedUntilMillis: Long?): String =
    if (mutedUntilMillis == null) {
        stringResource(R.string.chat_muted)
    } else {
        stringResource(
            R.string.chat_muted_until,
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(mutedUntilMillis)),
        )
    }
