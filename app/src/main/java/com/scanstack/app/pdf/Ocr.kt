package com.scanstack.app.pdf

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

object Ocr {
    /**
     * Returns the document's text. Pages that already contain real text are read directly;
     * scanned pages are read with on-device text recognition (no internet needed).
     */
    suspend fun extractText(file: File, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): String {
        val total = withContext(Dispatchers.IO) { PdfTools.pageCount(file) }
        if (total <= 0) return ""
        val embedded = withContext(Dispatchers.IO) { PdfTools.embeddedText(file) }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val out = StringBuilder()
            for (i in 0 until total) {
                val direct = embedded.getOrNull(i).orEmpty()
                val text = if (direct.length >= 25) {
                    direct
                } else {
                    val bitmap = withContext(Dispatchers.IO) { PdfTools.renderPage(file, i, 1800) }
                    if (bitmap == null) {
                        ""
                    } else {
                        val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
                        bitmap.recycle()
                        result.text.trim()
                    }
                }
                if (total > 1) out.append("— Page ").append(i + 1).append(" —\n")
                out.append(text).append("\n\n")
                onProgress(i + 1, total)
            }
            return out.toString().trim()
        } finally {
            recognizer.close()
        }
    }
}
