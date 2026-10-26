package pl.larpnet.android.ui.chat

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.larpnet.android.R
import pl.larpnet.android.data.matrix.ChatImage
import pl.larpnet.android.data.matrix.MatrixRepository
import pl.larpnet.android.ui.common.ZoomableImage

/** Downscaled chat images, keyed by [ChatImage.sourceJson] -- bubbles scroll in and out of the
 * `LazyColumn`, and re-fetching and re-decoding each time would flicker. Bounded, unlike
 * `MatrixAvatarCache`: these are far bigger than avatars. */
private object ChatImageCache {
    private val images = LruCache<String, ImageBitmap>(40)
    fun get(key: String): ImageBitmap? = images.get(key)
    fun put(key: String, image: ImageBitmap) { images.put(key, image) }
}

private const val BUBBLE_MAX_SIDE = 240
private const val THUMBNAIL_MAX_PX = 720

private sealed class LoadState {
    data object Loading : LoadState()
    data object Failed : LoadState()
    data class Loaded(val image: ImageBitmap) : LoadState()
}

/** An `m.image` message's bubble: the picture itself (sized from the event's width/height while
 * it loads), tapping opens [ChatImageViewer]. Mirrors larpnet-iOS's `ChatImageView`. */
@Composable
fun ChatImageBubble(image: ChatImage, repository: MatrixRepository) {
    var showViewer by remember { mutableStateOf(false) }
    val state by produceState<LoadState>(
        initialValue = ChatImageCache.get(image.sourceJson)?.let { LoadState.Loaded(it) } ?: LoadState.Loading,
        key1 = image.sourceJson,
    ) {
        if (value is LoadState.Loaded) return@produceState
        value = runCatching {
            val bytes = repository.chatImage(image)
            val bitmap = withContext(Dispatchers.Default) { decodeDownscaled(bytes, THUMBNAIL_MAX_PX) }
            ChatImageCache.put(image.sourceJson, bitmap)
            LoadState.Loaded(bitmap)
        }.getOrElse { LoadState.Failed }
    }

    val loaded = (state as? LoadState.Loaded)?.image
    Box(
        modifier = Modifier
            .size(bubbleSize(image, loaded))
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = loaded != null) { showViewer = true },
        contentAlignment = Alignment.Center,
    ) {
        when (val current = state) {
            is LoadState.Loaded -> Image(
                bitmap = current.image,
                contentDescription = stringResource(R.string.chat_image),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            LoadState.Loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
            LoadState.Failed -> Icon(
                Icons.Filled.BrokenImage,
                contentDescription = stringResource(R.string.chat_image_load_failed),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (showViewer && loaded != null) {
        ChatImageViewer(image = image, preview = loaded, repository = repository, onDismiss = { showViewer = false })
    }
}

/** The event's own aspect ratio when it has one (the apps always send it; the web client
 * doesn't), else the loaded image's, else a 4:3 placeholder. */
private fun bubbleSize(image: ChatImage, loaded: ImageBitmap?): DpSize {
    val ratio = when {
        image.width != null && image.height != null && image.width > 0 && image.height > 0 ->
            image.width.toFloat() / image.height
        loaded != null && loaded.height > 0 -> loaded.width.toFloat() / loaded.height
        else -> 4f / 3f
    }
    return if (ratio >= 1f) {
        DpSize(BUBBLE_MAX_SIDE.dp, (BUBBLE_MAX_SIDE / ratio).dp)
    } else {
        DpSize((BUBBLE_MAX_SIDE * ratio).dp, BUBBLE_MAX_SIDE.dp)
    }
}

private fun decodeDownscaled(bytes: ByteArray, maxPx: Int): ImageBitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: error("undecodable image")
    return bitmap.asImageBitmap()
}

/** Full-screen, zoomable chat image -- shows the bubble's downscaled copy right away and swaps
 * in the full-resolution bytes once they're loaded. */
@Composable
private fun ChatImageViewer(image: ChatImage, preview: ImageBitmap, repository: MatrixRepository, onDismiss: () -> Unit) {
    val full by produceState<ByteArray?>(initialValue = null, key1 = image.sourceJson) {
        value = runCatching { repository.chatImage(image) }.getOrNull()
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            val bytes = full
            if (bytes != null) {
                ZoomableImage(model = bytes)
            } else {
                Image(
                    bitmap = preview,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White)
            }
        }
    }
}
