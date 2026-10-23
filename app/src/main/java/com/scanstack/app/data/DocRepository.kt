package com.scanstack.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.scanstack.app.pdf.PdfTools
import java.io.File
import java.io.IOException

data class Doc(
    val file: File,
    val name: String,
    /** -1 when the PDF is password protected. */
    val pages: Int,
    val sizeBytes: Long,
    val modified: Long,
)

/** All documents live as PDF files in the app's private "docs" folder. */
class DocRepository(private val context: Context) {

    val dir: File = File(context.filesDir, "docs").apply { mkdirs() }

    fun list(): List<Doc> =
        (dir.listFiles() ?: emptyArray())
            .filter { it.isFile && it.extension.equals("pdf", ignoreCase = true) }
            .map { Doc(it, it.nameWithoutExtension, PdfTools.pageCount(it), it.length(), it.lastModified()) }
            .sortedByDescending { it.modified }

    fun cleanName(name: String): String =
        name.trim()
            .removeSuffix(".pdf").removeSuffix(".PDF")
            .replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(80)
            .ifBlank { "Document" }

    /** A new, unused file path such as "Scan 2026-10-22 2215 (2).pdf". */
    fun newFile(baseName: String): File {
        val clean = cleanName(baseName)
        var file = File(dir, "$clean.pdf")
        var n = 2
        while (file.exists()) {
            file = File(dir, "$clean ($n).pdf")
            n++
        }
        return file
    }

    /** Copies a PDF from another app or the scanner into the library. */
    fun importUri(uri: Uri, baseName: String): File {
        val out = newFile(baseName)
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Can't read that file")
        try {
            input.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
        } catch (e: Exception) {
            out.delete()
            throw e
        }
        if (out.length() == 0L) {
            out.delete()
            throw IOException("That file is empty")
        }
        return out
    }

    fun rename(file: File, newName: String): File {
        if (cleanName(newName) == file.nameWithoutExtension) return file
        val target = newFile(newName)
        if (!file.renameTo(target)) throw IOException("Couldn't rename the file")
        return target
    }

    fun delete(file: File) {
        file.delete()
    }

    /** Replaces [file] with [replacement], keeping the original name. */
    fun replace(file: File, replacement: File) {
        if (!file.delete() || !replacement.renameTo(file)) {
            replacement.delete()
            throw IOException("Couldn't update the file")
        }
    }

    /** Scratch file in the same folder, so it can be renamed into place. Not listed (".tmp"). */
    fun tempFile(): File = File(dir, ".work-${System.nanoTime()}.tmp")

    /** Copies something from another app into a scratch file. */
    fun copyToTemp(uri: Uri): File {
        val out = tempFile()
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Can't read the scan")
        input.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
        return out
    }

    fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
}
