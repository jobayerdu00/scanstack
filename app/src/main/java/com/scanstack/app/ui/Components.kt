@file:OptIn(ExperimentalMaterial3Api::class)

package com.scanstack.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.scanstack.app.data.Doc
import com.scanstack.app.pdf.PdfTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- Page images ----------

object BitmapCache {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }
}

private sealed interface PageState {
    data object Loading : PageState
    data class Ready(val bitmap: Bitmap) : PageState
    data object Failed : PageState
}

/**
 * Shows one PDF page. With [fillWidth] the image takes the full width at the page's own
 * proportions; otherwise it fills [modifier]'s size.
 */
@Composable
fun PdfPageImage(
    file: File,
    page: Int,
    widthPx: Int,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = false,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val key = "${file.absolutePath}|${file.lastModified()}|$page|$widthPx"
    val state by produceState<PageState>(
        initialValue = BitmapCache.get(key)?.let { PageState.Ready(it) } ?: PageState.Loading,
        key1 = key,
    ) {
        val cached = BitmapCache.get(key)
        if (cached != null) {
            value = PageState.Ready(cached)
            return@produceState
        }
        value = PageState.Loading
        val bitmap = withContext(Dispatchers.IO) { PdfTools.renderPage(file, page, widthPx) }
        value = if (bitmap != null) {
            BitmapCache.put(key, bitmap)
            PageState.Ready(bitmap)
        } else {
            PageState.Failed
        }
    }

    when (val s = state) {
        is PageState.Ready -> {
            val m = if (fillWidth) {
                modifier.fillMaxWidth().aspectRatio(s.bitmap.width.toFloat() / s.bitmap.height)
            } else {
                modifier
            }
            Image(
                bitmap = s.bitmap.asImageBitmap(),
                contentDescription = "Page ${page + 1}",
                modifier = m.background(Color.White),
                contentScale = contentScale,
            )
        }
        else -> {
            val m = if (fillWidth) modifier.fillMaxWidth().aspectRatio(0.707f) else modifier
            Box(m.background(Color.White), contentAlignment = Alignment.Center) {
                if (s is PageState.Loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = AppColors.Teal)
                } else {
                    Icon(AppIcons.Lock, contentDescription = "Locked", tint = AppColors.TextMuted)
                }
            }
        }
    }
}

// ---------- Dialogs ----------

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    label: String,
    confirmText: String,
    supporting: String? = null,
    numbersAndSymbols: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Card,
        title = { Text(title) },
        text = {
            Column {
                if (supporting != null) {
                    Text(supporting, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
                    Spacer(Modifier.height(Space.m))
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(label) },
                    singleLine = true,
                    shape = RoundedCornerShape(Radius.m),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (numbersAndSymbols) KeyboardType.Uri else KeyboardType.Text,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = AppColors.TextSecondary) } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Card,
        icon = { ToolIcon(AppIcons.Trash, AppColors.Error, 44.dp, AppColors.ErrorSubtle) },
        title = { Text(title) },
        text = { Text(body, color = AppColors.TextSecondary) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Error, contentColor = Color.White),
            ) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = AppColors.TextSecondary) } },
    )
}

/** Blocking progress card shown while a file operation runs. */
@Composable
fun BusyDialog(label: String) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Card(
            shape = RoundedCornerShape(Radius.l),
            colors = CardDefaults.cardColors(containerColor = AppColors.Card),
        ) {
            Row(
                Modifier.padding(horizontal = Space.xl, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.l),
            ) {
                CircularProgressIndicator(
                    Modifier.size(28.dp),
                    strokeWidth = 3.dp,
                    color = AppColors.Teal,
                    trackColor = AppColors.TealSubtle,
                )
                Text(label, style = MaterialTheme.typography.bodyLarge, color = AppColors.Text)
            }
        }
    }
}

@Composable
fun MenuItem(text: String, icon: ImageVector, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) AppColors.Error else AppColors.Text
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodyMedium, color = color) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp)) },
        onClick = onClick,
    )
}

/** White app bar used on document and editing screens, with a hairline underneath. */
@Composable
fun appBarColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = AppColors.Card,
    scrolledContainerColor = AppColors.Card,
    titleContentColor = AppColors.Text,
    navigationIconContentColor = AppColors.Text,
    actionIconContentColor = AppColors.Text,
)

/** Standard back button. */
@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) { Icon(AppIcons.Back, contentDescription = "Back") }
}

/** Draws a 1 dp border-colored line along the top edge (for bottom bars). */
fun Modifier.topHairline(): Modifier = this.drawBehind {
    drawLine(AppColors.Border, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
}

/** Thin divider in the border color. */
@Composable
fun Hairline(modifier: Modifier = Modifier) = HorizontalDivider(modifier, thickness = 1.dp, color = AppColors.Border)

@Composable
fun Thumbnail(doc: Doc, modifier: Modifier = Modifier, widthPx: Int = 180) {
    if (doc.pages < 0) {
        Box(modifier.background(AppColors.BlueSubtle), contentAlignment = Alignment.Center) {
            Icon(AppIcons.Lock, contentDescription = "Locked", tint = AppColors.Primary, modifier = Modifier.size(20.dp))
        }
    } else {
        PdfPageImage(doc.file, 0, widthPx, modifier, contentScale = ContentScale.Crop)
    }
}

@Composable
fun HSpace(dp: Int) = Spacer(Modifier.width(dp.dp))

// ---------- Helpers ----------

fun shareFile(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share ${file.nameWithoutExtension}"))
}

fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Share text"))
}

fun copyFileTo(context: Context, file: File, target: Uri) {
    val out = context.contentResolver.openOutputStream(target) ?: throw java.io.IOException("Can't write there")
    out.use { o -> file.inputStream().use { it.copyTo(o) } }
}

fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024L * 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}

fun formatDate(ms: Long): String = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(ms))

fun timestamp(): String = SimpleDateFormat("yyyy-MM-dd HHmm", Locale.US).format(Date())

fun docInfo(doc: Doc): String {
    val pages = when {
        doc.pages < 0 -> "Locked"
        doc.pages == 1 -> "1 page"
        else -> "${doc.pages} pages"
    }
    return "$pages · ${formatSize(doc.sizeBytes)} · ${formatDate(doc.modified)}"
}

/** "1-3, 5" -> [0, 1, 2, 4]. Returns null if the text isn't valid for [pageCount] pages. */
fun parsePages(input: String, pageCount: Int): List<Int>? {
    val result = mutableListOf<Int>()
    for (part in input.split(',', ';').map { it.replace(" ", "") }.filter { it.isNotEmpty() }) {
        val range = part.split('-', '–')
        val start = range[0].toIntOrNull() ?: return null
        val end = if (range.size > 1) (range[1].toIntOrNull() ?: return null) else start
        if (range.size > 2 || start < 1 || end > pageCount || start > end) return null
        for (p in start..end) if ((p - 1) !in result) result.add(p - 1)
    }
    return result.ifEmpty { null }
}

fun friendlyError(e: Throwable): String {
    val name = e.javaClass.simpleName
    return when {
        name == "InvalidPasswordException" -> "Wrong password"
        e is OutOfMemoryError -> "This file is too large for your phone's memory"
        else -> "Something went wrong" + (e.message?.let { ": $it" } ?: "")
    }
}
