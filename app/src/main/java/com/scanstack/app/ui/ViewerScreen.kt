@file:OptIn(ExperimentalMaterial3Api::class)

package com.scanstack.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scanstack.app.data.DocRepository
import com.scanstack.app.pdf.CompressLevel
import com.scanstack.app.pdf.Ocr
import com.scanstack.app.pdf.PdfInfo
import com.scanstack.app.pdf.PdfTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private enum class ViewerDialog { Rename, Delete, Compress, Lock, Unlock, Extract }

@Composable
fun ViewerScreen(
    repo: DocRepository,
    file: File,
    snackbar: SnackbarHostState,
    message: (String) -> Unit,
    onBack: () -> Unit,
    onOpen: (File) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var reload by remember(file) { mutableIntStateOf(0) }
    var info by remember(file) { mutableStateOf<PdfInfo?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var dialog by remember(file) { mutableStateOf<ViewerDialog?>(null) }
    var ocrText by remember(file) { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }

    LaunchedEffect(file, reload) {
        if (!file.exists()) {
            onBack()
            return@LaunchedEffect
        }
        info = withContext(Dispatchers.IO) { PdfTools.info(file) }
    }

    fun work(label: String, block: suspend () -> Unit) {
        scope.launch {
            busy = label
            try {
                block()
            } catch (e: Exception) {
                message(friendlyError(e))
            } catch (e: OutOfMemoryError) {
                message(friendlyError(e))
            } finally {
                busy = null
            }
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        if (uri != null) {
            work("Saving…") {
                withContext(Dispatchers.IO) { copyFileTo(context, file, uri) }
                message("Saved to your phone")
            }
        }
    }

    val name = file.nameWithoutExtension
    val current = info
    val canEdit = current != null && !current.locked

    fun extractText() {
        work("Reading text…") {
            val text = Ocr.extractText(file) { done, total ->
                if (total > 1) busy = "Reading text… page $done of $total"
            }
            if (text.isBlank()) message("No text found in this document") else ocrText = text
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { shareFile(context, file) }) {
                        Icon(Icons.Outlined.Share, contentDescription = "Share")
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            MenuItem("Save to phone", Icons.Outlined.Download) {
                                menu = false
                                saveLauncher.launch(file.name)
                            }
                            MenuItem("Rename", Icons.Outlined.Edit) {
                                menu = false
                                dialog = ViewerDialog.Rename
                            }
                            if (canEdit) {
                                MenuItem("Extract pages", Icons.Outlined.ContentCut) {
                                    menu = false
                                    dialog = ViewerDialog.Extract
                                }
                            }
                            MenuItem("Delete", Icons.Outlined.Delete) {
                                menu = false
                                dialog = ViewerDialog.Delete
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (current != null) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        if (canEdit) {
                            BarAction("Get text", Icons.Outlined.TextFields) { extractText() }
                            BarAction("Compress", Icons.Outlined.Compress) { dialog = ViewerDialog.Compress }
                        }
                        if (current.encrypted) {
                            BarAction("Unlock", Icons.Outlined.LockOpen) { dialog = ViewerDialog.Unlock }
                        } else {
                            BarAction("Password", Icons.Outlined.Lock) { dialog = ViewerDialog.Lock }
                        }
                        BarAction("Share", Icons.Outlined.Share) { shareFile(context, file) }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceContainer),
        ) {
            when {
                current == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                current.locked -> LockedState(Modifier.align(Alignment.Center)) { dialog = ViewerDialog.Unlock }
                else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    val widthPx = with(LocalDensity.current) { (maxWidth - 32.dp).roundToPx() }.coerceIn(200, 1600)
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(current.pageCount) { index ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Card(
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    PdfPageImage(file, index, widthPx, fillWidth = true)
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "${index + 1} / ${current.pageCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ----- Dialogs -----
    when (dialog) {
        ViewerDialog.Rename -> TextInputDialog(
            title = "Rename",
            initial = name,
            label = "Name",
            confirmText = "Rename",
            onDismiss = { dialog = null },
            onConfirm = { newName ->
                dialog = null
                work("Renaming…") {
                    val renamed = withContext(Dispatchers.IO) { repo.rename(file, newName) }
                    if (renamed != file) onOpen(renamed)
                }
            },
        )

        ViewerDialog.Delete -> ConfirmDialog(
            title = "Delete this document?",
            body = "\"$name\" will be permanently deleted from ScanStack.",
            confirmText = "Delete",
            onDismiss = { dialog = null },
            onConfirm = {
                dialog = null
                repo.delete(file)
                message("Deleted")
                onBack()
            },
        )

        ViewerDialog.Compress -> CompressDialog(
            onDismiss = { dialog = null },
            onConfirm = { level ->
                dialog = null
                work("Compressing…") {
                    val out = repo.newFile("$name (compressed)")
                    withContext(Dispatchers.IO) { PdfTools.compress(file, out, level) }
                    val before = file.length()
                    val after = out.length()
                    if (after >= before) {
                        out.delete()
                        message("This PDF is already as small as it can get")
                    } else {
                        val saved = ((1 - after.toDouble() / before) * 100).roundToInt()
                        message("$saved% smaller: ${formatSize(before)} → ${formatSize(after)}")
                        onOpen(out)
                    }
                }
            },
        )

        ViewerDialog.Lock -> TextInputDialog(
            title = "Add a password",
            initial = "",
            label = "Password",
            confirmText = "Protect",
            supporting = "ScanStack makes a protected copy and keeps this one as it is. " +
                "Write the password down: the copy can't be opened without it.",
            onDismiss = { dialog = null },
            onConfirm = { password ->
                dialog = null
                work("Adding password…") {
                    val out = repo.newFile("$name (protected)")
                    withContext(Dispatchers.IO) { PdfTools.protect(file, out, password) }
                    message("Created a password-protected copy")
                    onOpen(out)
                }
            },
        )

        ViewerDialog.Unlock -> TextInputDialog(
            title = "Remove password",
            initial = "",
            label = "Current password",
            confirmText = "Unlock",
            onDismiss = { dialog = null },
            onConfirm = { password ->
                dialog = null
                work("Unlocking…") {
                    val tmp = repo.tempFile()
                    try {
                        withContext(Dispatchers.IO) {
                            PdfTools.unlock(file, tmp, password)
                            repo.replace(file, tmp)
                        }
                    } finally {
                        tmp.delete()
                    }
                    message("Password removed")
                    reload++
                }
            },
        )

        ViewerDialog.Extract -> {
            val count = current?.pageCount ?: 0
            TextInputDialog(
                title = "Extract pages",
                initial = "",
                label = "Pages, e.g. 1-3, 5",
                confirmText = "Extract",
                supporting = "Copies the pages you choose into a new PDF. This document has $count " +
                    (if (count == 1) "page." else "pages."),
                numbersAndSymbols = true,
                onDismiss = { dialog = null },
                onConfirm = { input ->
                    val pages = parsePages(input, count)
                    if (pages == null) {
                        message("Enter page numbers between 1 and $count, like 1-3, 5")
                    } else {
                        dialog = null
                        work("Extracting pages…") {
                            val label = input.replace(" ", "")
                            val out = repo.newFile("$name (pages $label)")
                            withContext(Dispatchers.IO) { PdfTools.extractPages(file, pages, out) }
                            onOpen(out)
                        }
                    }
                },
            )
        }

        null -> Unit
    }

    ocrText?.let { text ->
        AlertDialog(
            onDismissRequest = { ocrText = null },
            title = { Text("Text from this document") },
            text = {
                SelectionContainer {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { shareText(context, text) }) { Text("Share") }
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(text))
                        message("Text copied")
                    }) { Text("Copy all") }
                }
            },
            dismissButton = { TextButton(onClick = { ocrText = null }) { Text("Close") } },
        )
    }

    busy?.let { BusyDialog(it) }
}

@Composable
private fun BarAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(
        Modifier
            .clickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun LockedState(modifier: Modifier = Modifier, onUnlock: () -> Unit) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Outlined.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text("This PDF is password protected", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            "You can still share it. Enter the password to view and edit it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onUnlock) { Text("Enter password") }
    }
}

@Composable
private fun CompressDialog(onDismiss: () -> Unit, onConfirm: (CompressLevel) -> Unit) {
    var level by remember { mutableStateOf(CompressLevel.Medium) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compress PDF") },
        text = {
            Column {
                Text(
                    "Makes a smaller copy. Text in the copy can't be selected, so use Get text first if you need it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                CompressLevel.entries.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = level == option, onClick = { level = option }, role = Role.RadioButton)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = level == option, onClick = null)
                        HSpace(8)
                        Column {
                            Text(option.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                option.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(level) }) { Text("Compress") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
