package com.ChronosFlow.VBCR.core.ui

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.InputStream

fun decodeThumbnail(stream: InputStream, targetPx: Int = 320): ImageBitmap? {
    val bytes = stream.readBytes()
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    val raw = maxOf(opts.outWidth, opts.outHeight)
    if (raw <= 0) return null
    var sample = 1
    while (raw / sample > targetPx) sample *= 2
    val opts2 = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts2)?.asImageBitmap()
}
