package com.scanstack.app.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Converts Word documents (.docx) and plain text files to PDF, entirely on the phone.
 * Keeps paragraphs, headings, bold/italic/underline, font sizes, colors, alignment,
 * bullet lists, tables, pictures and page breaks. Complex layouts (text boxes,
 * columns, headers/footers) are simplified.
 */
object WordToPdf {

    private const val PAGE_W = 595 // A4 in points
    private const val PAGE_H = 842
    private const val MARGIN = 56f
    private const val BASE_SIZE = 11f

    private sealed interface Block
    private class Para(
        val text: CharSequence,
        val align: Layout.Alignment,
        val spaceBefore: Float,
        val spaceAfter: Float,
        val breakBefore: Boolean,
    ) : Block
    private class Picture(val bytes: ByteArray, val widthPt: Float?) : Block
    private class Table(val rows: List<List<CharSequence>>) : Block
    private object PageBreak : Block

    enum class Kind { Docx, OldDoc, Text, Unknown }

    fun detect(context: Context, uri: Uri, name: String): Kind {
        val head = ByteArray(8)
        val read = context.contentResolver.openInputStream(uri)?.use { it.read(head) } ?: 0
        return when {
            read >= 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() -> Kind.Docx
            read >= 4 && head[0] == 0xD0.toByte() && head[1] == 0xCF.toByte() -> Kind.OldDoc
            name.endsWith(".txt", ignoreCase = true) -> Kind.Text
            else -> Kind.Unknown
        }
    }

    fun convert(context: Context, uri: Uri, kind: Kind, out: File) {
        val blocks = when (kind) {
            Kind.Docx -> readDocx(context, uri)
            Kind.Text -> readText(context, uri)
            Kind.OldDoc -> throw IOException("Old .doc files aren't supported. Open it in Word and save as .docx first.")
            Kind.Unknown -> throw IOException("This isn't a Word (.docx) file")
        }
        render(blocks, out)
    }

    // ---------------------------------------------------------------- reading

    private fun readText(context: Context, uri: Uri): List<Block> {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw IOException("Can't read that file")
        return text.replace("\r\n", "\n").split('\n').map {
            Para(it, Layout.Alignment.ALIGN_NORMAL, 0f, 4f, false)
        }
    }

    private fun readDocx(context: Context, uri: Uri): List<Block> {
        val files = HashMap<String, ByteArray>()
        var total = 0L
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val n = entry.name
                    val wanted = n == "word/document.xml" || n == "word/_rels/document.xml.rels" ||
                        n.startsWith("word/media/")
                    if (wanted && !entry.isDirectory) {
                        val bytes = zip.readBytes()
                        total += bytes.size
                        if (total > 80L * 1024 * 1024) throw IOException("This document is too large")
                        files[n] = bytes
                    }
                }
            }
        } ?: throw IOException("Can't read that file")

        val document = files["word/document.xml"] ?: throw IOException("This isn't a Word (.docx) file")
        val rels = parseRels(files["word/_rels/document.xml.rels"])
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(ByteArrayInputStream(document), "UTF-8")

        val blocks = mutableListOf<Block>()
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) break
            if (event != XmlPullParser.START_TAG) continue
            when (local(parser.name)) {
                "p" -> blocks += parseParagraph(parser, rels, files)
                "tbl" -> blocks += parseTable(parser, rels, files)
            }
        }
        return blocks
    }

    private fun parseRels(bytes: ByteArray?): Map<String, String> {
        if (bytes == null) return emptyMap()
        val map = HashMap<String, String>()
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(ByteArrayInputStream(bytes), "UTF-8")
        while (true) {
            val e = p.next()
            if (e == XmlPullParser.END_DOCUMENT) break
            if (e == XmlPullParser.START_TAG && local(p.name) == "Relationship") {
                val id = p.getAttributeValue(null, "Id")
                val target = p.getAttributeValue(null, "Target")
                if (id != null && target != null) map[id] = target
            }
        }
        return map
    }

    private fun local(name: String): String = name.substringAfter(':')

    private fun attr(p: XmlPullParser, name: String): String =
        p.getAttributeValue(null, "w:$name") ?: p.getAttributeValue(null, name) ?: ""

    private fun onOff(p: XmlPullParser): Boolean {
        val v = attr(p, "val")
        return v.isEmpty() || v == "1" || v.equals("true", true) || v.equals("on", true)
    }

    private fun styleSize(style: String): Float {
        val s = style.lowercase()
        return when {
            s == "title" -> 26f
            s == "subtitle" -> 15f
            s.startsWith("heading1") || s == "heading 1" -> 20f
            s.startsWith("heading2") -> 16f
            s.startsWith("heading3") -> 14f
            s.startsWith("heading") -> 12f
            else -> BASE_SIZE
        }
    }

    private fun styleBold(style: String): Boolean {
        val s = style.lowercase()
        return s == "title" || s.startsWith("heading")
    }

    /** Reads one <w:p>. Returns the paragraph plus any pictures and page breaks in it. */
    private fun parseParagraph(p: XmlPullParser, rels: Map<String, String>, files: Map<String, ByteArray>): List<Block> {
        val depth = p.depth
        val sb = SpannableStringBuilder()
        val extra = mutableListOf<Block>()
        var style = ""
        var align = Layout.Alignment.ALIGN_NORMAL
        var isList = false
        var level = 0
        var before = 0f
        var after = 6f
        var breakBefore = false
        var pageBreakAfter = false
        var inPPr = false
        var inRPr = false
        var bold: Boolean? = null
        var italic: Boolean? = null
        var underline = false
        var strike = false
        var size: Float? = null
        var color: Int? = null
        var extentPt: Float? = null

        fun append(text: String) {
            if (text.isEmpty()) return
            val start = sb.length
            sb.append(text)
            val end = sb.length
            val flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            val b = bold ?: styleBold(style)
            val i = italic ?: false
            val typeface = when {
                b && i -> Typeface.BOLD_ITALIC
                b -> Typeface.BOLD
                i -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            if (typeface != Typeface.NORMAL) sb.setSpan(StyleSpan(typeface), start, end, flags)
            sb.setSpan(AbsoluteSizeSpan((size ?: styleSize(style)).roundToInt()), start, end, flags)
            if (underline) sb.setSpan(UnderlineSpan(), start, end, flags)
            if (strike) sb.setSpan(StrikethroughSpan(), start, end, flags)
            color?.let { sb.setSpan(ForegroundColorSpan(it), start, end, flags) }
        }

        while (true) {
            val event = p.next()
            if (event == XmlPullParser.END_DOCUMENT) break
            if (event == XmlPullParser.END_TAG) {
                when (local(p.name)) {
                    "p" -> if (p.depth == depth) break
                    "pPr" -> inPPr = false
                    "rPr" -> inRPr = false
                }
                continue
            }
            if (event != XmlPullParser.START_TAG) continue
            val n = local(p.name)
            when {
                n == "pPr" -> inPPr = true
                n == "rPr" -> inRPr = true
                inPPr && !inRPr -> when (n) {
                    "pStyle" -> style = attr(p, "val")
                    "jc" -> align = when (attr(p, "val")) {
                        "center" -> Layout.Alignment.ALIGN_CENTER
                        "right", "end" -> Layout.Alignment.ALIGN_OPPOSITE
                        else -> Layout.Alignment.ALIGN_NORMAL
                    }
                    "numPr" -> isList = true
                    "ilvl" -> level = attr(p, "val").toIntOrNull() ?: 0
                    "spacing" -> {
                        attr(p, "before").toFloatOrNull()?.let { before = it / 20f }
                        attr(p, "after").toFloatOrNull()?.let { after = it / 20f }
                    }
                    "pageBreakBefore" -> breakBefore = onOff(p)
                }
                inPPr -> Unit // formatting of the paragraph mark itself: ignore
                n == "r" -> {
                    bold = null; italic = null; underline = false; strike = false; size = null; color = null
                }
                inRPr -> when (n) {
                    "b" -> bold = onOff(p)
                    "i" -> italic = onOff(p)
                    "u" -> underline = attr(p, "val").let { it.isNotEmpty() && it != "none" }
                    "strike" -> strike = onOff(p)
                    "sz" -> size = attr(p, "val").toFloatOrNull()?.div(2f)
                    "color" -> color = parseColor(attr(p, "val"))
                }
                n == "t" -> append(p.nextText())
                n == "tab" -> append("\t")
                n == "br" -> if (attr(p, "type") == "page") pageBreakAfter = true else append("\n")
                n == "cr" -> append("\n")
                n == "extent" -> extentPt = p.getAttributeValue(null, "cx")?.toFloatOrNull()?.div(12700f)
                n == "blip" -> {
                    val id = p.getAttributeValue(null, "r:embed")
                    val target = id?.let { rels[it] }
                    val path = target?.let { t -> if (t.startsWith("/")) t.removePrefix("/") else "word/" + t.removePrefix("./") }
                    val bytes = path?.let { files[it] }
                    if (bytes != null) extra += Picture(bytes, extentPt)
                    extentPt = null
                }
            }
        }

        if (isList && sb.isNotEmpty()) {
            val prefix = "    ".repeat(level.coerceIn(0, 6)) + "•  "
            sb.insert(0, prefix)
            sb.setSpan(AbsoluteSizeSpan(styleSize(style).roundToInt()), 0, prefix.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val heading = styleBold(style)
        val result = mutableListOf<Block>()
        result += Para(
            text = sb,
            align = align,
            spaceBefore = if (heading && before == 0f) 12f else before,
            spaceAfter = if (isList && after == 6f) 2f else after,
            breakBefore = breakBefore,
        )
        result += extra
        if (pageBreakAfter) result += PageBreak
        return result
    }

    private fun parseTable(p: XmlPullParser, rels: Map<String, String>, files: Map<String, ByteArray>): Block {
        val depth = p.depth
        val rows = mutableListOf<List<CharSequence>>()
        var row: MutableList<CharSequence>? = null
        var cell: SpannableStringBuilder? = null
        while (true) {
            val event = p.next()
            if (event == XmlPullParser.END_DOCUMENT) break
            if (event == XmlPullParser.END_TAG) {
                when (local(p.name)) {
                    "tbl" -> if (p.depth == depth) break
                    "tc" -> {
                        row?.add(cell ?: "")
                        cell = null
                    }
                    "tr" -> {
                        row?.let { rows += it }
                        row = null
                    }
                }
                continue
            }
            if (event != XmlPullParser.START_TAG) continue
            when (local(p.name)) {
                "tr" -> row = mutableListOf()
                "tc" -> cell = SpannableStringBuilder()
                "p" -> {
                    val parts = parseParagraph(p, rels, files)
                    val text = (parts.firstOrNull() as? Para)?.text ?: ""
                    val c = cell ?: SpannableStringBuilder().also { cell = it }
                    if (c.isNotEmpty()) c.append("\n")
                    c.append(text)
                }
                "tbl" -> {
                    // Table inside a table cell: keep its text
                    val inner = parseTable(p, rels, files) as Table
                    val c = cell ?: SpannableStringBuilder().also { cell = it }
                    inner.rows.forEach { r ->
                        if (c.isNotEmpty()) c.append("\n")
                        c.append(r.joinToString("  |  "))
                    }
                }
            }
        }
        return Table(rows)
    }

    private fun parseColor(v: String): Int? {
        if (v.length != 6 || v.equals("auto", true)) return null
        return runCatching { Color.parseColor("#$v") }.getOrNull()
    }

    // ---------------------------------------------------------------- drawing

    private fun render(blocks: List<Block>, out: File) {
        val pdf = PdfDocument()
        val contentW = PAGE_W - 2 * MARGIN
        val bottom = PAGE_H - MARGIN
        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var y = MARGIN

        fun newPage() {
            page?.let { pdf.finishPage(it) }
            pageNumber++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
            y = MARGIN
        }

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = BASE_SIZE
            color = Color.BLACK
        }
        val cellPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f
            color = Color.BLACK
        }
        val border = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.6f
            color = Color.rgb(140, 140, 140)
        }
        val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG)

        fun layout(text: CharSequence, paint: TextPaint, width: Int, align: Layout.Alignment): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, max(width, 10))
                .setAlignment(align)
                .setLineSpacing(0f, 1.15f)
                .setIncludePad(false)
                .build()

        /** Draws a text layout, continuing on new pages when it doesn't fit. */
        fun drawLayout(l: StaticLayout) {
            var line = 0
            while (line < l.lineCount) {
                if (y >= bottom - 4f) newPage()
                val available = bottom - y
                val top = l.getLineTop(line)
                var end = line
                while (end < l.lineCount && l.getLineBottom(end) - top <= available) end++
                if (end == line) {
                    if (y > MARGIN) {
                        newPage()
                        continue
                    }
                    end = line + 1
                }
                val endBottom = l.getLineBottom(end - 1)
                val canvas = page!!.canvas
                canvas.save()
                canvas.translate(MARGIN, y - top)
                canvas.clipRect(0f, top.toFloat(), contentW, endBottom.toFloat())
                l.draw(canvas)
                canvas.restore()
                y += (endBottom - top).toFloat()
                line = end
            }
        }

        newPage()
        for (block in blocks) {
            when (block) {
                is PageBreak -> newPage()
                is Para -> {
                    if (block.breakBefore && y > MARGIN) newPage()
                    y += block.spaceBefore
                    if (block.text.isEmpty()) {
                        y += BASE_SIZE * 1.15f
                        if (y > bottom) newPage()
                    } else {
                        drawLayout(layout(block.text, textPaint, contentW.toInt(), block.align))
                    }
                    y += block.spaceAfter
                }
                is Picture -> {
                    val bitmap = decode(block.bytes) ?: continue
                    var w = min(contentW, block.widthPt ?: (bitmap.width * 0.75f))
                    var h = w * bitmap.height / bitmap.width
                    val maxH = bottom - MARGIN
                    if (h > maxH) {
                        w *= maxH / h
                        h = maxH
                    }
                    if (y + h > bottom) newPage()
                    page!!.canvas.drawBitmap(bitmap, null, RectF(MARGIN, y, MARGIN + w, y + h), imagePaint)
                    bitmap.recycle()
                    y += h + 8f
                }
                is Table -> {
                    val cols = block.rows.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: continue
                    val colW = contentW / cols
                    val pad = 4f
                    for (row in block.rows) {
                        val layouts = row.map { layout(it, cellPaint, (colW - 2 * pad).toInt(), Layout.Alignment.ALIGN_NORMAL) }
                        val rowH = (layouts.maxOfOrNull { it.height } ?: 10) + 2 * pad
                        if (y + rowH > bottom && y > MARGIN) newPage()
                        val canvas = page!!.canvas
                        for (i in 0 until cols) {
                            val x = MARGIN + i * colW
                            canvas.drawRect(x, y, x + colW, y + rowH, border)
                            val l = layouts.getOrNull(i) ?: continue
                            canvas.save()
                            canvas.translate(x + pad, y + pad)
                            canvas.clipRect(0f, 0f, colW - 2 * pad, rowH - 2 * pad)
                            l.draw(canvas)
                            canvas.restore()
                        }
                        y += rowH
                    }
                    y += 10f
                }
            }
        }
        page?.let { pdf.finishPage(it) }
        try {
            out.outputStream().use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null // EMF/WMF and other formats Android can't draw
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
