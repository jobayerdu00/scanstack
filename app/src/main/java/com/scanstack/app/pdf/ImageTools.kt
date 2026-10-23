package com.scanstack.app.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import kotlin.math.max

object ImageTools {

    /** Loads a picture from the phone, upright and no bigger than [maxSide] pixels. */
    fun load(context: Context, uri: Uri, maxSide: Int = 1600): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= 28) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val longest = max(info.size.width, info.size.height)
                    if (longest > maxSide) {
                        val f = maxSide.toFloat() / longest
                        decoder.setTargetSize(
                            (info.size.width * f).toInt().coerceAtLeast(1),
                            (info.size.height * f).toInt().coerceAtLeast(1),
                        )
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                }
            }?.let { toArgb(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun toArgb(b: Bitmap): Bitmap =
        if (b.config == Bitmap.Config.ARGB_8888 && b.isMutable) b else b.copy(Bitmap.Config.ARGB_8888, true)

    /**
     * Makes white/light paper transparent so a photographed signature or logo
     * sits cleanly on the page. Dark ink keeps its colour.
     */
    fun removeWhiteBackground(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val c = px[i]
            val a = c ushr 24
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val lum = (r * 299 + g * 587 + b * 114) / 1000
            val keep = ((215 - lum) * 255 / 120).coerceIn(0, 255) // 215+ -> gone, 95- -> solid
            val alpha = a * keep / 255
            px[i] = (alpha shl 24) or (c and 0x00FFFFFF)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    /** Trims empty (transparent) space around the picture. */
    fun cropToContent(src: Bitmap, padding: Int = 6): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                if ((px[row + x] ushr 24) > 24) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < 0) return src
        val left = (minX - padding).coerceAtLeast(0)
        val top = (minY - padding).coerceAtLeast(0)
        val right = (maxX + padding).coerceAtMost(w - 1)
        val bottom = (maxY + padding).coerceAtMost(h - 1)
        return Bitmap.createBitmap(src, left, top, right - left + 1, bottom - top + 1)
    }

    /** Copy of [src] with its transparency multiplied by [opacity] (0..1). */
    fun withOpacity(src: Bitmap, opacity: Float): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = (opacity.coerceIn(0f, 1f) * 255).toInt() }
        Canvas(out).drawBitmap(src, 0f, 0f, paint)
        return out
    }
}
