package com.scanstack.app.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.text.TextPaint
import android.util.LruCache
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import kotlin.math.ceil

enum class StampKind { Signature, Text, Check, Cross }

/**
 * Something placed on a page in Fill & Sign.
 * [x], [y] = top-left corner and [width] = width, all as fractions of the page (0..1).
 * The height follows from the stamp image's proportions.
 */
data class Stamp(
    val id: Long,
    val page: Int,
    val kind: StampKind,
    val x: Float,
    val y: Float,
    val width: Float,
    val text: String = "",
    val color: Int = INK,
) {
    companion object {
        const val INK = 0xFF111827.toInt()
    }
}

/** Makes the picture for each stamp. Text is drawn as an image so every language works. */
object StampArt {
    /** Font size used for text stamp images. Width on the page comes from [Stamp.width]. */
    const val TEXT_PX = 120f

    private val cache = LruCache<String, Bitmap>(64)

    fun bitmap(stamp: Stamp, signature: Bitmap?): Bitmap? = when (stamp.kind) {
        StampKind.Signature -> signature
        StampKind.Text -> cached("t|${stamp.color}|${stamp.text}") { textBitmap(stamp.text, stamp.color) }
        StampKind.Check -> cached("c|${stamp.color}") { markBitmap(check = true, color = stamp.color) }
        StampKind.Cross -> cached("x|${stamp.color}") { markBitmap(check = false, color = stamp.color) }
    }

    private fun cached(key: String, make: () -> Bitmap): Bitmap =
        cache.get(key) ?: make().also { cache.put(key, it) }

    fun textBitmap(text: String, color: Int): Bitmap {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TEXT_PX
            this.color = color
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val label = text.ifBlank { " " }
        val fm = paint.fontMetrics
        val w = ceil(paint.measureText(label)).toInt().coerceAtLeast(8) + 8
        val h = ceil(fm.descent - fm.ascent).toInt().coerceAtLeast(8)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawText(label, 4f, -fm.ascent, paint)
        return bitmap
    }

    private fun markBitmap(check: Boolean, color: Int): Bitmap {
        val s = 240
        val bitmap = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = s * 0.12f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val path = Path()
        if (check) {
            path.moveTo(s * 0.16f, s * 0.54f)
            path.lineTo(s * 0.40f, s * 0.78f)
            path.lineTo(s * 0.86f, s * 0.24f)
        } else {
            path.moveTo(s * 0.2f, s * 0.2f)
            path.lineTo(s * 0.8f, s * 0.8f)
            path.moveTo(s * 0.8f, s * 0.2f)
            path.lineTo(s * 0.2f, s * 0.8f)
        }
        Canvas(bitmap).drawPath(path, paint)
        return bitmap
    }

    /** Width (fraction of page) for a new text stamp at a normal reading size. */
    fun defaultTextWidth(bitmap: Bitmap): Float = bitmap.width / TEXT_PX * 0.024f
}

/** The user's saved signature (a transparent PNG in the app's private storage). */
object SignatureStore {
    private fun file(context: Context) = File(context.filesDir, "signature.png")

    fun load(context: Context): Bitmap? =
        file(context).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }

    fun save(context: Context, bitmap: Bitmap) {
        file(context).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

object StampWriter {
    /** Draws all stamps onto their pages and saves the result to [out]. */
    fun apply(file: File, out: File, stamps: List<Stamp>, signature: Bitmap?) {
        PDDocument.load(file).use { doc ->
            stamps.groupBy { it.page }.forEach { (pageIndex, onPage) ->
                if (pageIndex < 0 || pageIndex >= doc.numberOfPages) return@forEach
                val page = doc.getPage(pageIndex)
                val box = page.cropBox
                val rotation = ((page.rotation % 360) + 360) % 360
                val sideways = rotation == 90 || rotation == 270
                // Size of the page as the user sees it (after rotation)
                val viewW = if (sideways) box.height else box.width
                val viewH = if (sideways) box.width else box.height
                // Maps "as seen" coordinates (origin bottom-left) to the page's own coordinates
                val toPage = when (rotation) {
                    90 -> Matrix(0f, 1f, -1f, 0f, box.width + box.lowerLeftX, box.lowerLeftY)
                    180 -> Matrix(-1f, 0f, 0f, -1f, box.width + box.lowerLeftX, box.height + box.lowerLeftY)
                    270 -> Matrix(0f, -1f, 1f, 0f, box.lowerLeftX, box.height + box.lowerLeftY)
                    else -> Matrix(1f, 0f, 0f, 1f, box.lowerLeftX, box.lowerLeftY)
                }
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.transform(toPage)
                    for (stamp in onPage) {
                        val bitmap = StampArt.bitmap(stamp, signature) ?: continue
                        val image = LosslessFactory.createFromImage(doc, bitmap)
                        val w = stamp.width * viewW
                        val h = w * bitmap.height / bitmap.width
                        val x = stamp.x * viewW
                        val y = viewH - stamp.y * viewH - h
                        cs.drawImage(image, x, y, w, h)
                    }
                }
            }
            doc.save(out)
        }
    }
}
