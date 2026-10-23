package com.scanstack.app.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import java.io.File
import kotlin.math.max

/**
 * Brightness / contrast / color settings. All values are -1..1 (0 = unchanged).
 * [auto] stretches each page so the paper becomes white and the ink stays dark.
 */
data class EnhanceSettings(
    val auto: Boolean = true,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val color: Float = 0f,
)

enum class EnhancePreset(val label: String, val settings: EnhanceSettings) {
    Original("Original", EnhanceSettings(auto = false)),
    Auto("Auto", EnhanceSettings(auto = true, brightness = 0.05f, contrast = 0.1f, color = 0.1f)),
    Bright("Bright", EnhanceSettings(auto = true, brightness = 0.25f, contrast = 0.15f, color = 0.2f)),
    Vivid("Vivid color", EnhanceSettings(auto = true, brightness = 0.15f, contrast = 0.25f, color = 0.55f)),
}

object Enhance {

    /** Black and white points of a page (darkest ink and brightest paper), from a luminance histogram. */
    fun levels(bitmap: Bitmap): Pair<Int, Int> {
        val hist = IntArray(256)
        val stepX = max(1, bitmap.width / 300)
        val stepY = max(1, bitmap.height / 300)
        var total = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val c = bitmap.getPixel(x, y)
                val lum = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
                hist[lum]++
                total++
                x += stepX
            }
            y += stepY
        }
        fun percentile(p: Float): Int {
            val target = (total * p).toInt()
            var sum = 0
            for (i in 0..255) {
                sum += hist[i]
                if (sum >= target) return i
            }
            return 255
        }
        val black = percentile(0.01f).coerceAtMost(80)
        val white = percentile(0.96f).coerceAtLeast(black + 60).coerceAtMost(255)
        return black to white
    }

    /** The 4x5 color matrix for [s] (row-major, offsets in 0..255), for Android and Compose. */
    fun matrix(s: EnhanceSettings, levels: Pair<Int, Int>?): FloatArray {
        val m = ColorMatrix()
        if (s.auto && levels != null) {
            val (black, white) = levels
            val k = 255f / max(1, white - black)
            val t = -black * k
            m.set(floatArrayOf(k, 0f, 0f, 0f, t, 0f, k, 0f, 0f, t, 0f, 0f, k, 0f, t, 0f, 0f, 0f, 1f, 0f))
        }
        val c = 1f + s.contrast * 0.6f
        val t = (1f - c) * 128f + s.brightness * 80f
        m.postConcat(ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f)))
        m.postConcat(ColorMatrix().apply { setSaturation((1f + s.color).coerceIn(0f, 2f)) })
        return m.array
    }

    /** Applies [s] to every page and saves a new PDF. Pages become images (text is no longer selectable). */
    fun apply(file: File, out: File, s: EnhanceSettings, dpi: Int = 200) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                PDDocument().use { doc ->
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            val wPts = page.width.toFloat()
                            val hPts = page.height.toFloat()
                            val wPx = (wPts * dpi / 72f).toInt().coerceIn(1, 2600)
                            val hPx = (hPts * wPx / wPts).toInt().coerceAtLeast(1)
                            val src = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
                            src.eraseColor(Color.WHITE)
                            page.render(src, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val dst = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
                            val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                                colorFilter = ColorMatrixColorFilter(matrix(s, levels(src)))
                            }
                            Canvas(dst).drawBitmap(src, 0f, 0f, paint)
                            src.recycle()
                            val image = JPEGFactory.createFromImage(doc, dst, 0.88f)
                            val pdPage = PDPage(PDRectangle(wPts, hPts))
                            doc.addPage(pdPage)
                            PDPageContentStream(doc, pdPage).use { it.drawImage(image, 0f, 0f, wPts, hPts) }
                            dst.recycle()
                        }
                    }
                    doc.save(out)
                }
            }
        }
    }
}
