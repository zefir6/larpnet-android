package pl.larpnet.android.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import pl.larpnet.android.data.model.MediaAttachment

/** What [MediaGalleryDialog] needs: the full attachment list (for swiping between images)
 * plus which one was tapped. Mirrors the iOS app's `MediaGalleryContext`. */
data class GalleryContext(val attachments: List<MediaAttachment>, val initialIndex: Int)

/**
 * Full-screen, swipeable, pinch-to-zoom / double-tap-to-zoom image viewer -- the "enlarge the
 * image" behavior a media thumbnail tap previously had no handler for at all, so the tap fell
 * through to StatusCard's "open thread" gesture instead. Android analogue of the iOS app's
 * `MediaGalleryView` (`.fullScreenCover(item:)` there, a full-screen `Dialog` here -- same
 * "local overlay state, not a nav route" shape).
 */
@Composable
fun MediaGalleryDialog(context: GalleryContext, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            val pagerState = rememberPagerState(initialPage = context.initialIndex) { context.attachments.size }
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                ZoomableImage(url = context.attachments[page].url)
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White)
            }
        }
    }
}

/** Pinch-to-zoom (1x-5x) with pan-while-zoomed, plus double-tap to toggle between 1x and 2.5x --
 * matching the standard photo-viewer gesture set (see iOS's `ZoomableImageView`). */
@Composable
private fun ZoomableImage(url: String?) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y,
            )
            // A plain detectTransformGestures() reacts to single-finger drags too (it treats
            // them as "pan" with zoom=1), which meant this was consuming the swipe gesture
            // meant for HorizontalPager's own paging -- confirmed live: swiping between images
            // in a multi-image post did nothing. Only engage here for actual multi-touch pinch,
            // or once already zoomed in (where panning the zoomed image, not paging, is the
            // expected gesture) -- otherwise leave the event unconsumed so it reaches the pager.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1 || scale > 1f) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                            scale = newScale
                            offset = if (newScale > 1f) offset + panChange else Offset.Zero
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                )
            },
    )
}
