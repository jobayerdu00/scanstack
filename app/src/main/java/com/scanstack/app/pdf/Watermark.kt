package com.scanstack.app.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class WatermarkLayout(val label: String) {
    Diagonal("Diagonal"),
    Center("Center"),
    Tiled("Tiled"),
    Corner("Corner"),
}

/** One copy of the watermark: centre point, size, and angle (degrees, counter-clockwise). Origin is top-left. */
data class Placement(val cx: Float, val cy: Float, val w: Float, val h: Float, val angle: Float)

object Watermark {

    fun textBitmap(text: String, color: Int): Bitmap {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 200f
            this.color = color
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.06f
        }
        val fm = paint.fontMetrics
        val pad = 24f
        val w = (ceil(paint.measureText(text)) + 2 * pad).toInt().coerceAtLeast(8)
        val h = ceil(fm.descent - fm.ascent + pad).toInt().coerceAtLeast(8)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawText(text, pad, -fm.ascent + pad / 2f, paint)
        return bitmap
    }

    /**
     * Where the watermark goes on a page of [pageW] x [pageH] (any unit).
     * [aspect] = image width / height, [scale] = size slider (0..1).
     */
    fun placements(layout: WatermarkLayout, pageW: Float, pageH: Float, aspect: Float, scale: Float): List<Placement> {
        val s = scale.coerceIn(0.05f, 1f)
        return when (layout) {
            WatermarkLayout.Diagonal -> {
                val angle = Math.toDegrees(atan2(pageH.toDouble(), pageW.toDouble())).toFloat()
                val w = s * hypot(pageW, pageH)
                listOf(Placement(pageW / 2f, pageH / 2f, w, w / aspect, angle))
            }
            WatermarkLayout.Center -> {
                val w = s * pageW
                listOf(Placement(pageW / 2f, pageH / 2f, w, w / aspect, 0f))
            }
            WatermarkLayout.Corner -> {
                val w = s * pageW * 0.6f
                val h = w / aspect
                val m = 0.04f * min(pageW, pageH)
                listOf(Placement(pageW - m - w / 2f, pageH - m - h / 2f, w, h, 0f))
            }
            WatermarkLayout.Tiled -> {
                val w = s * pageW * 0.55f
                val h = w / aspect
                val stepX = w * 1.35f
                val stepY = max(h * 3.2f, w * 0.6f)
                val list = mutableListOf<Placement>()
                var row = -1
                while (row * stepY < pageH + stepY) {
                    val offset = if (row % 2 == 0) 0f else stepX / 2f
                    var col = -1
                    while (col * stepX < pageW + stepX) {
                        list += Placement(col * stepX + offset, row * stepY + stepY / 2f, w, h, 30f)
                        col++
                    }
                    row++
                }
                list
            }
        }
    }

    /** Stamps [art] on every page and saves to [out]. */
    fun apply(file: File, out: File, art: Bitmap, layout: WatermarkLayout, scale: Float, opacity: Float) {
        val faded = ImageTools.withOpacity(art, opacity)
        val aspect = art.width.toFloat() / art.height
        PDDocument.load(file).use { doc ->
            val image = LosslessFactory.createFromImage(doc, faded)
            for (page in doc.pages) {
                val g = PageGeometry.of(page)
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.transform(g.toPage)
                    for (p in placements(layout, g.width, g.height, aspect, scale)) {
                        cs.saveGraphicsState()
                        // Page coordinates have y going up, so flip the centre point
                        cs.transform(Matrix.getRotateInstance(Math.toRadians(p.angle.toDouble()), p.cx, g.height - p.cy))
                        cs.drawImage(image, -p.w / 2f, -p.h / 2f, p.w, p.h)
                        cs.restoreGraphicsState()
                    }
                }
            }
            doc.save(out)
        }
    }
}
