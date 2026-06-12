package com.chronosflow.core.notifications

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat

/**
 * Renders the round, brand-tinted category badge used as a notification large icon — a filled
 * accent-colored circle with a white glyph centered on top. Cheap (one small ARGB bitmap) and
 * safe to call off the main thread from broadcast delivery.
 */
internal object NotificationIcons {
    fun categoryBadge(
        context: Context,
        @DrawableRes iconRes: Int,
        @ColorInt backgroundColor: Int,
        sizePx: Int = 96
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = sizePx / 2f
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = backgroundColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(radius, radius, radius, circlePaint)

        val glyph = ContextCompat.getDrawable(context, iconRes)?.mutate()
        if (glyph != null) {
            DrawableCompat.setTint(glyph, Color.WHITE)
            val padding = (sizePx * 0.26f).toInt()
            glyph.setBounds(padding, padding, sizePx - padding, sizePx - padding)
            glyph.draw(canvas)
        }
        return bitmap
    }
}
