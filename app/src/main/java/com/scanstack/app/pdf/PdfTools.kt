package com.scanstack.app.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

data class PdfInfo(
    val pageCount: Int,
    /** True when the PDF can't be opened without a password. */
    val locked: Boolean,
    /** True when the PDF has any password protection. */
    val encrypted: Boolean,
)

/** Compression strength: render resolution (dpi) and JPEG quality. */
enum class CompressLevel(val label: String, val detail: String, val dpi: Int, val quality: Float) {
    Light("Light", "Best quality, smaller file", 150, 0.80f),
    Medium("Medium", "Good for email and uploads", 110, 0.60f),
    Strong("Strong", "Smallest file, lower quality", 80, 0.45f),
}

object PdfTools {

    /** Number of pages, or -1 if the PDF can't be opened (usually password protected). */
    fun pageCount(file: File): Int {
        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { it.pageCount }
            }
        } catch (e: Exception) {
            -1
        }
    }

    fun info(file: File): PdfInfo {
        val count = pageCount(file)
        val encrypted = try {
            PDDocument.load(file).use { it.isEncrypted }
        } catch (e: InvalidPasswordException) {
            true
        } catch (e: Exception) {
            false
        }
        return PdfInfo(
            pageCount = max(count, 0),
            locked = count < 0,
            encrypted = encrypted || count < 0,
        )
    }

    /** Draws one page as a bitmap that is [widthPx] wide. */
    fun renderPage(file: File, index: Int, widthPx: Int): Bitmap? {
        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (index < 0 || index >= renderer.pageCount) return null
                    renderer.openPage(index).use { page ->
                        val w = widthPx.coerceIn(16, 2400)
                        val h = (w.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Adds the pages of [extra] to the end of [file], writing the result to [out]. */
    fun append(file: File, extra: File, out: File) = merge(listOf(file, extra), out)

    fun merge(files: List<File>, out: File) {
        val merger = PDFMergerUtility()
        files.forEach { merger.addSource(it) }
        merger.destinationFileName = out.absolutePath
        merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
    }

    /** Copies the given pages (0-based) into a new PDF. */
    fun extractPages(file: File, pages: List<Int>, out: File) {
        PDDocument.load(file).use { src ->
            PDDocument().use { dst ->
                pages.forEach { dst.importPage(src.getPage(it)) }
                dst.save(out)
            }
        }
    }

    /** Removes the given pages (0-based) and saves the rest to [out]. */
    fun deletePages(file: File, pages: Collection<Int>, out: File) {
        PDDocument.load(file).use { doc ->
            pages.distinct().sortedDescending().forEach { if (it in 0 until doc.numberOfPages) doc.removePage(it) }
            doc.save(out)
        }
    }

    /** Rebuilds the PDF from compressed page images. Text is no longer selectable afterwards. */
    fun compress(file: File, out: File, level: CompressLevel) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                PDDocument().use { doc ->
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            val wPts = page.width.toFloat()
                            val hPts = page.height.toFloat()
                            val wPx = (wPts * level.dpi / 72f).toInt().coerceIn(1, 3000)
                            val hPx = (hPts * wPx / wPts).toInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val image = JPEGFactory.createFromImage(doc, bitmap, level.quality)
                            val pdPage = PDPage(PDRectangle(wPts, hPts))
                            doc.addPage(pdPage)
                            PDPageContentStream(doc, pdPage).use { it.drawImage(image, 0f, 0f, wPts, hPts) }
                            bitmap.recycle()
                        }
                    }
                    doc.save(out)
                }
            }
        }
    }

    /**
     * Puts both sides of an ID card on one A4 page at real card size (85.6 x 54 mm),
     * front on the upper half and back on the lower half, like a photocopy.
     */
    fun idCardPdf(context: Context, front: Uri, back: Uri?, out: File) {
        val cardLong = 85.6f / 25.4f * 72f // mm -> points
        val cardShort = 54f / 25.4f * 72f
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            val pw = PDRectangle.A4.width
            val ph = PDRectangle.A4.height
            val sides = listOfNotNull(front, back)
            PDPageContentStream(doc, page).use { cs ->
                sides.forEachIndexed { i, uri ->
                    val bitmap = ImageTools.load(context, uri, 2000) ?: throw IOException("Couldn't read the scan")
                    val portrait = bitmap.height > bitmap.width
                    // Fit the scan inside a card-sized box with the same orientation
                    val boxW = if (portrait) cardShort else cardLong
                    val boxH = if (portrait) cardLong else cardShort
                    val scale = min(boxW / bitmap.width, boxH / bitmap.height)
                    val w = bitmap.width * scale
                    val h = bitmap.height * scale
                    // Centres at 30% and 70% down the page (PDF y goes up)
                    val centerY = if (sides.size == 1) ph / 2f else if (i == 0) ph * 0.70f else ph * 0.30f
                    val image = JPEGFactory.createFromImage(doc, bitmap, 0.92f)
                    cs.drawImage(image, (pw - w) / 2f, centerY - h / 2f, w, h)
                    bitmap.recycle()
                }
            }
            doc.save(out)
        }
    }

    /** Puts each photo on its own A4 page (landscape photos get a landscape page). */
    fun imagesToPdf(context: Context, uris: List<Uri>, out: File) {
        PDDocument().use { doc ->
            for (uri in uris) {
                val bitmap = decodeScaled(context, uri, 2200) ?: continue
                val image = JPEGFactory.createFromImage(doc, bitmap, 0.85f)
                val a4 = PDRectangle.A4
                val size = if (bitmap.width > bitmap.height) PDRectangle(a4.height, a4.width) else a4
                val page = PDPage(size)
                doc.addPage(page)
                val margin = 18f
                val scale = min(
                    (size.width - 2 * margin) / bitmap.width,
                    (size.height - 2 * margin) / bitmap.height,
                )
                val w = bitmap.width * scale
                val h = bitmap.height * scale
                PDPageContentStream(doc, page).use {
                    it.drawImage(image, (size.width - w) / 2f, (size.height - h) / 2f, w, h)
                }
                bitmap.recycle()
            }
            if (doc.numberOfPages == 0) throw IOException("None of the photos could be read")
            doc.save(out)
        }
    }

    fun protect(file: File, out: File, password: String) {
        PDDocument.load(file).use { doc ->
            val policy = StandardProtectionPolicy(password, password, AccessPermission())
            policy.encryptionKeyLength = 128
            doc.protect(policy)
            doc.save(out)
        }
    }

    /** Throws InvalidPasswordException when the password is wrong. */
    fun unlock(file: File, out: File, password: String) {
        PDDocument.load(file, password).use { doc ->
            doc.isAllSecurityToBeRemoved = true
            doc.save(out)
        }
    }

    /** Text already stored in the PDF, one entry per page (empty for scanned pages). */
    fun embeddedText(file: File): List<String> {
        return try {
            PDDocument.load(file).use { doc ->
                val stripper = PDFTextStripper()
                (1..doc.numberOfPages).map { p ->
                    stripper.startPage = p
                    stripper.endPage = p
                    stripper.getText(doc).trim()
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun decodeScaled(context: Context, uri: Uri, maxSide: Int): Bitmap? {
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
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            }
        } catch (e: Exception) {
            null
        }
    }
}
