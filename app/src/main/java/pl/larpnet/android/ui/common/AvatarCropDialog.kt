package pl.larpnet.android.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.larpnet.android.R
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

private const val OUTPUT_SIZE = 512
private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 3f

/**
 * Square, center-locked crop (pinch to zoom in, no panning) -- avoids the pan-translation-math
 * this app has no live way to visually verify pixel-by-pixel right now (see plan doc's avatar
 * crop notes), while still giving real crop control. Confirmed circular *guide* only: the
 * exported bitmap is a square JPEG, matching the iOS app's own crop (its circular mask is also
 * visual-only, not an alpha-clipped export) -- the server is expected to apply its own display mask.
 */
@Composable
fun AvatarCropDialog(imageUri: Uri, onCrop: (ByteArray) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var zoom by remember { mutableStateOf(MIN_ZOOM) }

    LaunchedEffect(imageUri) {
        sourceBitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(imageUri)?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.avatar_crop_title), style = MaterialTheme.typography.titleMedium)

                val bitmap = sourceBitmap
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .size(280.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    if (bitmap == null) {
                        CircularProgressIndicator(color = Color.White)
                    } else {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(scaleX = zoom, scaleY = zoom)
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, _, gestureZoom, _ ->
                                        zoom = (zoom * gestureZoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                                    }
                                },
                        )
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(color = Color.White.copy(alpha = 0.7f), style = Stroke(width = 2.dp.toPx()))
                        }
                    }
                }

                Row(modifier = Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
                    TextButton(
                        onClick = { bitmap?.let { onCrop(renderCrop(it, zoom)) } },
                        enabled = bitmap != null,
                    ) {
                        Text(stringResource(R.string.avatar_crop_confirm))
                    }
                }
            }
        }
    }
}

/** Mirrors exactly what's on screen: [ContentScale.Crop] center-crops to a square first, then
 * [zoom] shrinks the visible region around that same center -- so the crop square's side is
 * simply the pre-zoom square's side divided by [zoom]. */
private fun renderCrop(source: Bitmap, zoom: Float): ByteArray {
    val squareSide = minOf(source.width, source.height)
    val cropSide = (squareSide / zoom).roundToInt().coerceIn(1, squareSide)
    val left = (source.width - cropSide) / 2
    val top = (source.height - cropSide) / 2

    val output = createBitmap(OUTPUT_SIZE, OUTPUT_SIZE)
    Canvas(output).drawBitmap(
        source,
        Rect(left, top, left + cropSide, top + cropSide),
        Rect(0, 0, OUTPUT_SIZE, OUTPUT_SIZE),
        null,
    )
    val stream = ByteArrayOutputStream()
    output.compress(Bitmap.CompressFormat.JPEG, 90, stream)
    return stream.toByteArray()
}
