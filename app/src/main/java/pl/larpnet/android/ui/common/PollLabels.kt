package pl.larpnet.android.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import pl.larpnet.android.R

/** Label for one of [pl.larpnet.android.ui.compose.POLL_EXPIRY_CHOICES]. */
@Composable
fun pollExpiryLabel(seconds: Int): String = when (seconds) {
    300 -> stringResource(R.string.poll_expiry_5m)
    1800 -> stringResource(R.string.poll_expiry_30m)
    3600 -> stringResource(R.string.poll_expiry_1h)
    21600 -> stringResource(R.string.poll_expiry_6h)
    86400 -> stringResource(R.string.poll_expiry_1d)
    259200 -> stringResource(R.string.poll_expiry_3d)
    604800 -> stringResource(R.string.poll_expiry_1w)
    2629746 -> stringResource(R.string.poll_expiry_1mo)
    else -> seconds.toString()
}
