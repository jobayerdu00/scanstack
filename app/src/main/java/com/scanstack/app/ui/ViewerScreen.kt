@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.scanstack.app.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.outlined.NoteAdd
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
    initialAction: String?,
    onActionConsumed: () -> Unit,
    snackbar: SnackbarHostState,
    message: (String) -> Unit,
    onBack: () -> Unit,
    onOpen: (File) -> Unit,
    onReplace: (File) -> Unit,
    onTool: (Tool) -> Unit,
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

    // ----- Page selection (long-press a page) -----
    var selecting by remember(file) { mutableStateOf(false) }
    val selectedPages = remember(file) { mutableStateListOf<Int>() }
    var confirmDeletePages by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    fun exitSelection() {
        selecting = false
        selectedPages.clear()
    }
    fun togglePage(index: Int) {
        if (index in selectedPages) selectedPages.remove(index) else selectedPages.add(index)
    }
    BackHandler(enabled = selecting) { exitSelection() }

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

    // ----- Add pages with the camera scanner -----
    val listState = rememberLazyListState()
    val addPageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val scan = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
        val uri = scan?.pdf?.uri ?: return@rememberLauncherForActivityResult
        val added = scan?.pdf?.pageCount ?: 0
        work("Adding pages…") {
            val before = info?.pageCount ?: 0
            withContext(Dispatchers.IO) {
                val scanned = repo.copyToTemp(uri)
                val merged = repo.tempFile()
                try {
                    PdfTools.append(file, scanned, merged)
                    repo.replace(file, merged)
                } finally {
                    scanned.delete()
                    merged.delete()
                }
            }
            reload++
            message(if (added == 1) "Page added" else "$added pages added")
            if (before > 0) {
                kotlinx.coroutines.delay(300)
                runCatching { listState.animateScrollToItem(before + 1) }
            }
        }
    }

    fun addPage() {
        val activity = context as? ComponentActivity ?: return
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(20)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(activity)
            .addOnSuccessListener { sender -> addPageLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
            .addOnFailureListener { e -> message("The scanner isn't available on this phone (${e.message})") }
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

    // Opened from a tool on the dashboard: start that tool once the document is ready
    LaunchedEffect(current, initialAction) {
        if (current == null || initialAction == null) return@LaunchedEffect
        onActionConsumed()
        if (current.locked && initialAction != "password") {
            dialog = ViewerDialog.Unlock
            return@LaunchedEffect
        }
        when (initialAction) {
            "compress" -> dialog = ViewerDialog.Compress
            "text" -> extractText()
            "extract" -> dialog = ViewerDialog.Extract
            "password" -> dialog = if (current.encrypted) ViewerDialog.Unlock else ViewerDialog.Lock
        }
    }

    Scaffold(
        topBar = {
            if (selecting) {
                val total = info?.pageCount ?: 0
                TopAppBar(
                    title = { Text("${selectedPages.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { exitSelection() }) {
                            Icon(AppIcons.Close, contentDescription = "Cancel selection")
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            if (selectedPages.size == total) {
                                selectedPages.clear()
                            } else {
                                selectedPages.clear()
                                selectedPages.addAll(0 until total)
                            }
                        }) {
                            Text(if (selectedPages.size == total && total > 0) "Unselect all" else "Select all")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = AppColors.TealSubtle,
                        titleContentColor = AppColors.TealDark,
                        navigationIconContentColor = AppColors.TealDark,
                        actionIconContentColor = AppColors.TealDark,
                    ),
                )
            } else TopAppBar(
     colors = appBarColors(),
                title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.Back, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { shareFile(context, file) }) {
                        Icon(AppIcons.Share, contentDescription = "Share")
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(AppIcons.More, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            MenuItem("Save to phone", AppIcons.Download) {
                                menu = false
                                saveLauncher.launch(file.name)
                            }
                            MenuItem("Rename", AppIcons.Edit) {
                                menu = false
                                dialog = ViewerDialog.Rename
                            }
                            if (canEdit) {
                                MenuItem("Extract pages", AppIcons.Scissors) {
                                    menu = false
                                    dialog = ViewerDialog.Extract
                                }
                            }
                            MenuItem("Delete", AppIcons.Trash, danger = true) {
                                menu = false
                                dialog = ViewerDialog.Delete
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (selecting) {
                Surface(color = AppColors.Card, modifier = Modifier.topHairline()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                val pages = selectedPages.sorted()
                                work("Copying pages…") {
                                    val label = if (pages.size == 1) "page ${pages[0] + 1}" else "${pages.size} pages"
                                    val out = repo.newFile("$name ($label)")
                                    withContext(Dispatchers.IO) { PdfTools.extractPages(file, pages, out) }
                                    exitSelection()
                                    onOpen(out)
                                }
                            },
                            enabled = selectedPages.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(AppIcons.Scissors, contentDescription = null, modifier = Modifier.size(18.dp))
                            HSpace(6)
                            Text("New PDF")
                        }
                        Button(
                            onClick = { confirmDeletePages = true },
                            enabled = selectedPages.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(AppIcons.Trash, contentDescription = null, modifier = Modifier.size(18.dp))
                            HSpace(6)
                            Text(if (selectedPages.isEmpty()) "Delete" else "Delete (${selectedPages.size})")
                        }
                    }
                }
            } else if (current != null) {
                Surface(color = AppColors.Card, modifier = Modifier.topHairline()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (canEdit) {
                            item { BarButton("Add page", AppIcons.AddPage, AppColors.TealDark, AppColors.TealSubtle) { addPage() } }
                            item { BarAction(Tool.Sign) { onTool(Tool.Sign) } }
                            item { BarAction(Tool.Watermark) { onTool(Tool.Watermark) } }
                            item { BarAction(Tool.Form) { onTool(Tool.Form) } }
                            item { BarAction(Tool.Text) { extractText() } }
                            item { BarAction(Tool.Compress) { dialog = ViewerDialog.Compress } }
                        }
                        item {
                            if (current.encrypted) {
                                BarAction(Tool.Protect, "Unlock") { dialog = ViewerDialog.Unlock }
                            } else {
                                BarAction(Tool.Protect) { dialog = ViewerDialog.Lock }
                            }
                        }
                        if (canEdit) item { BarAction(Tool.Extract, "Pages") { dialog = ViewerDialog.Extract } }
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
                .background(AppColors.Canvas),
        ) {
            when {
                current == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                current.locked -> LockedState(Modifier.align(Alignment.Center)) { dialog = ViewerDialog.Unlock }
                else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    val widthPx = with(LocalDensity.current) { (maxWidth - 32.dp).roundToPx() }.coerceIn(200, 1600)
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (current.pageCount > 1 && !selecting) {
                            item {
                                Text(
                                    "Tip: press and hold a page to select, delete or copy pages",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        items(current.pageCount) { index ->
                            val isSelected = selecting && index in selectedPages
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Card(
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                    shape = RoundedCornerShape(Radius.s),
                                    colors = CardDefaults.cardColors(containerColor = AppColors.Card),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            if (isSelected) 3.dp else 1.dp,
                                            if (isSelected) AppColors.Teal else AppColors.Border,
                                            RoundedCornerShape(Radius.s),
                                        )
                                        .combinedClickable(
                                            onClick = { if (selecting) togglePage(index) },
                                            onLongClick = {
                                                if (canEdit && current.pageCount > 0) {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    if (!selecting) {
                                                        selecting = true
                                                        selectedPages.clear()
                                                        selectedPages.add(index)
                                                    } else {
                                                        togglePage(index)
                                                    }
                                                }
                                            },
                                        ),
                                ) {
                                    Box {
                                        PdfPageImage(file, index, widthPx, fillWidth = true)
                                        if (selecting) {
                                            if (isSelected) {
                                                Box(
                                                    Modifier
                                                        .matchParentSize()
                                                        .background(AppColors.Teal.copy(alpha = 0.10f)),
                                                )
                                            }
                                            Box(
                                                Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(10.dp)
                                                    .size(30.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isSelected) AppColors.Teal
                                                        else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f),
                                                    )
                                                    .border(
                                                        2.dp,
                                                        if (isSelected) AppColors.Teal else AppColors.TextMuted,
                                                        CircleShape,
                                                    ),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                if (isSelected) {
                                                    Icon(
                                                        AppIcons.Check,
                                                        contentDescription = "Selected",
                                                        tint = androidx.compose.ui.graphics.Color.White,
                                                        modifier = Modifier.size(18.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }
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
                    if (renamed != file) onReplace(renamed)
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

    if (confirmDeletePages) {
        val total = info?.pageCount ?: 0
        val count = selectedPages.size
        if (count >= total) {
            AlertDialog(
                onDismissRequest = { confirmDeletePages = false },
                title = { Text("Can't delete every page") },
                text = { Text("A PDF needs at least one page. To remove the whole document, use Delete in the ⋮ menu.") },
                confirmButton = { TextButton(onClick = { confirmDeletePages = false }) { Text("OK") } },
            )
        } else {
            ConfirmDialog(
                title = if (count == 1) "Delete this page?" else "Delete $count pages?",
                body = "This removes the selected " + (if (count == 1) "page" else "pages") + " from \"$name\". It can't be undone.",
                confirmText = "Delete",
                onDismiss = { confirmDeletePages = false },
                onConfirm = {
                    confirmDeletePages = false
                    val pages = selectedPages.toList()
                    work("Deleting pages…") {
                        val tmp = repo.tempFile()
                        try {
                            withContext(Dispatchers.IO) {
                                PdfTools.deletePages(file, pages, tmp)
                                repo.replace(file, tmp)
                            }
                        } finally {
                            tmp.delete()
                        }
                        exitSelection()
                        reload++
                        message(if (pages.size == 1) "Page deleted" else "${pages.size} pages deleted")
                    }
                },
            )
        }
    }

    busy?.let { BusyDialog(it) }
}

@Composable
private fun BarAction(tool: Tool, label: String = tool.label, onClick: () -> Unit) =
    BarButton(label, tool.icon, tool.tint, onClick)

@Composable
private fun BarButton(
    label: String,
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    tile: androidx.compose.ui.graphics.Color = AppColors.BlueSubtle,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick, role = Role.Button)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ToolIcon(icon, tint, 40.dp, tile)
        Spacer(Modifier.height(Space.xs))
        Text(label, style = MaterialTheme.typography.labelMedium, color = AppColors.Text, maxLines = 1)
    }
}

@Composable
private fun LockedState(modifier: Modifier = Modifier, onUnlock: () -> Unit) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ToolIcon(AppIcons.Lock, size = 64.dp)
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
