package pl.larpnet.android.ui.common

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import pl.larpnet.android.data.matrix.MatrixRepository

/** Process-lifetime in-memory cache of decoded avatar thumbnails, keyed by `mxc://` URL -- keeps
 * [MatrixAvatarImage] from re-fetching (and re-decoding) the same image every time a message
 * bubble/room row scrolls back into view. Not persisted to disk: avatars are small and cheap
 * enough to refetch on a cold launch, and this avoids owning a cache-invalidation story for
 * someone changing their avatar. */
private object MatrixAvatarCache {
    private val images = mutableMapOf<String, ImageBitmap>()
    fun get(url: String): ImageBitmap? = images[url]
    fun put(url: String, image: ImageBitmap) { images[url] = image }
}

/**
 * A chat avatar. For a local user ([userId] set), the Friendica profile photo is tried first
 * (see [MatrixRepository.friendicaAvatarUrl] for why); if that fails, or there's no [userId],
 * it shows the real Matrix profile photo when one is set, falling back to
 * [InitialsAvatar] while it loads or when there isn't one -- used for both per-message sender
 * avatars (`ChatThreadScreen`) and room-list rows (`ChatScreen`), so a real photo replaces the
 * initials placeholder wherever the SDK has one instead of the app inventing its own avatar
 * pipeline per call site. Mirrors larpnet-iOS's `MatrixAvatarView`.
 */
@Composable
fun MatrixAvatarImage(
    avatarUrl: String?,
    name: String,
    repository: MatrixRepository,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    userId: String? = null,
) {
    val friendicaUrl = remember(userId) { repository.friendicaAvatarUrl(userId) }
    var friendicaFailed by remember(friendicaUrl) { mutableStateOf(false) }
    if (friendicaUrl != null && !friendicaFailed) {
        // Initials underneath while the photo loads, so the row never flashes empty.
        Box(modifier = modifier.size(size)) {
            InitialsAvatar(name = name, size = size)
            AsyncImage(
                model = friendicaUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                onError = { friendicaFailed = true },
                modifier = Modifier.size(size).clip(CircleShape),
            )
        }
        return
    }
    MatrixThumbnailAvatar(avatarUrl = avatarUrl, name = name, repository = repository, modifier = modifier, size = size)
}

@Composable
private fun MatrixThumbnailAvatar(
    avatarUrl: String?,
    name: String,
    repository: MatrixRepository,
    modifier: Modifier,
    size: Dp,
) {
    val image by produceState<ImageBitmap?>(
        initialValue = avatarUrl?.let { MatrixAvatarCache.get(it) },
        key1 = avatarUrl,
    ) {
        if (avatarUrl == null) {
            value = null
            return@produceState
        }
        MatrixAvatarCache.get(avatarUrl)?.let {
            value = it
            return@produceState
        }
        val bytes = runCatching { repository.avatarThumbnail(avatarUrl) }.getOrNull() ?: return@produceState
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() ?: return@produceState
        MatrixAvatarCache.put(avatarUrl, bitmap)
        value = bitmap
    }

    val loaded = image
    if (loaded != null) {
        Image(
            bitmap = loaded,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape),
        )
    } else {
        InitialsAvatar(name = name, modifier = modifier, size = size)
    }
}
