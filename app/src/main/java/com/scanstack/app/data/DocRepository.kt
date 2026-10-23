package com.scanstack.app.data

import android.content.Context
import android.net.Uri
import android.media.MediaScannerConnection
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

/**
 * All documents are PDF files in one folder: "Internal storage/ScanStack" by default
 * (see [StorageLocation]), or the app's private folder until storage access is granted.
 */
class DocRepository(private val context: Context) {

    /** The current documents folder. Looked up each time, since the user can change it. */
    val dir: File get() = StorageLocation.activeFolder(context)

    fun list(): List<Doc> {
        val folder = dir
        // Access was just granted: bring documents over from the app's private folder.
        val private = StorageLocation.privateFolder(context)
        if (folder != private) moveAll(private, folder)
        val docs = (folder.listFiles() ?: emptyArray())
            .filter { it.isFile && it.extension.equals("pdf", ignoreCase = true) }
            .map { Doc(it, it.nameWithoutExtension, PdfTools.pageCount(it), it.length(), it.lastModified()) }
            .sortedByDescending { it.modified }
        if (folder != private && docs.isNotEmpty()) {
            // Lets file managers and computers (over USB) see new or changed files right away.
            MediaScannerConnection.scanFile(context, docs.map { it.file.absolutePath }.toTypedArray(), null, null)
        }
        return docs
    }

    /** Moves every PDF from [from] to [to], renaming on clashes. Returns how many moved. */
    fun moveAll(from: File, to: File): Int {
        if (from.absoluteFile == to.absoluteFile) return 0
        val files = from.listFiles { f -> f.isFile && f.extension.equals("pdf", ignoreCase = true) } ?: return 0
        if (files.isEmpty()) return 0
        to.mkdirs()
        var moved = 0
        for (f in files) {
            var target = File(to, f.name)
            var n = 2
            while (target.exists()) {
                target = File(to, "${f.nameWithoutExtension} ($n).pdf")
                n++
            }
            val ok = f.renameTo(target) || runCatching {
                f.inputStream().use { i -> target.outputStream().use { o -> i.copyTo(o) } }
                target.setLastModified(f.lastModified())
                f.delete()
            }.isSuccess
            if (ok) moved++ else target.delete()
        }
        from.listFiles { f -> f.name.startsWith(".work-") }?.forEach { it.delete() }
        return moved
    }

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
