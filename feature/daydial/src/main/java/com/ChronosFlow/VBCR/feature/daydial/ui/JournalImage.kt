package com.ChronosFlow.VBCR.feature.daydial.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads a downsampled thumbnail [ImageBitmap] from a content [uri], or null while loading / on
 * failure. The project has no image-loading library, so this decodes via the platform
 * [BitmapFactory] off the main thread, using a bounds pass to pick an [inSampleSize] near [targetPx]
 * so large photos never decode at full resolution. Re-runs only when the uri changes.
 */
@Composable
internal fun rememberContentThumbnail(uri: String, targetPx: Int = 320): ImageBitmap? {
    val context = androidx.compose.ui.platform.LocalContext.current
    var bitmap by remember(uri, targetPx) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri, targetPx) {
        bitmap = withContext(Dispatchers.IO) { decodeThumbnail(context, uri, targetPx) }
    }
    return bitmap
}

private fun decodeThumbnail(context: Context, uri: String, targetPx: Int): ImageBitmap? = runCatching {
    val parsed = Uri.parse(uri)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
    var sample = 1
    while (maxDim > 0 && maxDim / sample > targetPx) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    context.contentResolver.openInputStream(parsed)?.use {
        BitmapFactory.decodeStream(it, null, opts)?.asImageBitmap()
    }
}.getOrNull()
