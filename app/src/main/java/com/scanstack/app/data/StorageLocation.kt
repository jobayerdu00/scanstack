package com.scanstack.app.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Where ScanStack keeps its documents.
 *
 * By default that's "Internal storage/ScanStack" so files are easy to find in any file
 * manager or from a computer. Writing there needs storage access ("All files access" on
 * Android 11+). Until the user grants it, documents are kept privately inside the app and
 * are moved to the public folder automatically once access is given.
 */
object StorageLocation {

    const val DEFAULT_FOLDER = "ScanStack"
    private const val PREFS = "storage"
    private const val KEY_FOLDER = "folder"
    private const val KEY_ASKED = "asked"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Top of internal shared storage, e.g. /storage/emulated/0 */
    fun storageRoot(): File = Environment.getExternalStorageDirectory()

    fun defaultFolder(): File = File(storageRoot(), DEFAULT_FOLDER)

    /** Private fallback folder used without storage access. */
    fun privateFolder(context: Context): File = File(context.filesDir, "docs")

    fun hasAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    /** The folder the user chose (or the default), whether or not access is granted yet. */
    fun chosenFolder(context: Context): File =
        prefs(context).getString(KEY_FOLDER, null)?.let { File(it) } ?: defaultFolder()

    fun setChosenFolder(context: Context, folder: File) {
        prefs(context).edit().putString(KEY_FOLDER, folder.absolutePath).apply()
    }

    fun resetToDefault(context: Context) {
        prefs(context).edit().remove(KEY_FOLDER).apply()
    }

    /** The folder documents are actually read from and written to right now. */
    fun activeFolder(context: Context): File {
        val folder = if (hasAccess(context)) chosenFolder(context) else privateFolder(context)
        if (!folder.exists()) folder.mkdirs()
        return if (folder.isDirectory && folder.canWrite()) folder else privateFolder(context).apply { mkdirs() }
    }

    fun isPublic(context: Context): Boolean = activeFolder(context) != privateFolder(context)

    /** Whether we already showed the first-run "save to Internal storage/ScanStack?" prompt. */
    fun wasAsked(context: Context): Boolean = prefs(context).getBoolean(KEY_ASKED, false)

    fun markAsked(context: Context) {
        prefs(context).edit().putBoolean(KEY_ASKED, true).apply()
    }

    /** "/storage/emulated/0/ScanStack" -> "Internal storage/ScanStack" */
    fun displayPath(folder: File): String {
        val root = storageRoot().absolutePath
        val path = folder.absolutePath
        return when {
            path == root -> "Internal storage"
            path.startsWith("$root/") -> "Internal storage/" + path.removePrefix("$root/")
            path.startsWith("/storage/") -> "SD card/" + path.removePrefix("/storage/").substringAfter('/')
            else -> path
        }
    }

    /** Turns a folder picked in the system folder chooser into a real path, if possible. */
    fun folderFromTreeUri(uri: Uri): File? {
        val id = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
        val volume = id.substringBefore(':')
        val relative = id.substringAfter(':', "")
        val base = if (volume.equals("primary", ignoreCase = true)) storageRoot() else File("/storage/$volume")
        return if (relative.isEmpty()) base else File(base, relative)
    }

    /** Opens the system screen where the user allows storage access (Android 11+). */
    fun allFilesAccessIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))

    fun allFilesAccessFallbackIntent(): Intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
}
