@file:OptIn(ExperimentalMaterial3Api::class)

package com.scanstack.app.ui

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.scanstack.app.R
import com.scanstack.app.data.Doc
import com.scanstack.app.data.DocRepository
import com.scanstack.app.data.StorageLocation
import com.scanstack.app.pdf.PdfTools
import com.scanstack.app.pdf.WordToPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

private enum class Tab { Home, Files, Tools }
private enum class SortBy(val label: String) { Recent("Recent"), Name("Name"), Size("Size") }

private val WORD_TYPES = arrayOf(
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/msword",
    "text/plain",
)

@Composable
fun HomeScreen(
    repo: DocRepository,
    activity: ComponentActivity,
    version: Int,
    snackbar: SnackbarHostState,
    message: (String) -> Unit,
    refresh: () -> Unit,
    onOpen: (File) -> Unit,
    onMerge: () -> Unit,
    onTool: (Tool, File) -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var docs by remember { mutableStateOf<List<Doc>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<Doc?>(null) }
    var deleteTarget by remember { mutableStateOf<Doc?>(null) }
    var pickFor by remember { mutableStateOf<Tool?>(null) }
    var importFor by remember { mutableStateOf<Tool?>(null) }

    // ----- First run: offer to save into Internal storage/ScanStack -----
    var showStorageIntro by remember {
        mutableStateOf(!StorageLocation.hasAccess(context) && !StorageLocation.wasAsked(context))
    }
    val requestStorage = rememberStorageAccessRequest { granted ->
        refresh()
        message(
            if (granted) "Documents are saved in ${StorageLocation.displayPath(StorageLocation.chosenFolder(context))}"
            else "No problem. You can turn this on later in Settings.",
        )
    }

    LaunchedEffect(version) {
        docs = withContext(Dispatchers.IO) { repo.list() }
        loaded = true
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

    // ----- Camera scanner -----
    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val pdfUri = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pdf?.uri
            ?: return@rememberLauncherForActivityResult
        work("Saving scan…") {
            val file = withContext(Dispatchers.IO) { repo.importUri(pdfUri, "Scan ${timestamp()}") }
            refresh()
            onOpen(file)
        }
    }

    fun startScan() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(50)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(activity)
            .addOnSuccessListener { sender -> scanLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
            .addOnFailureListener { e -> message("The scanner isn't available on this phone (${e.message})") }
    }

    // ----- ID card: scan the front, then the back, and put both on one page -----
    // Saved so the flow survives Android pausing the app while the camera is open
    var idFront by rememberSaveable { mutableStateOf<android.net.Uri?>(null) }
    var askForBack by rememberSaveable { mutableStateOf(false) }
    var showIdIntro by remember { mutableStateOf(false) }

    fun buildIdCard(front: android.net.Uri, back: android.net.Uri?) {
        idFront = null
        work("Creating ID card page…") {
            val out = repo.newFile("ID card ${timestamp()}")
            withContext(Dispatchers.IO) { PdfTools.idCardPdf(context, front, back, out) }
            refresh()
            message(if (back == null) "Saved the front side" else "Both sides saved on one page")
            onOpen(out)
        }
    }

    val idLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            // Cancelled while scanning the back: still save the front
            idFront?.let { buildIdCard(it, null) }
            return@rememberLauncherForActivityResult
        }
        val uri = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages?.firstOrNull()?.imageUri
            ?: return@rememberLauncherForActivityResult
        val front = idFront
        if (front == null) {
            idFront = uri
            askForBack = true
        } else {
            buildIdCard(front, uri)
        }
    }

    fun scanIdSide() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(activity)
            .addOnSuccessListener { sender -> idLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
            .addOnFailureListener { e -> message("The scanner isn't available on this phone (${e.message})") }
    }

    // ----- Import PDFs (optionally straight into a tool) -----
    val importPdfs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val tool = importFor
        importFor = null
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        work("Importing…") {
            val files = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching { repo.importUri(uri, repo.displayName(uri) ?: "Imported ${timestamp()}") }.getOrNull()
                }
            }
            refresh()
            when {
                files.isEmpty() -> message("Couldn't import those files")
                tool != null -> onTool(tool, files[0])
                files.size == 1 -> onOpen(files[0])
                else -> message("Imported ${files.size} PDFs")
            }
        }
    }

    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        work("Creating PDF…") {
            val out = repo.newFile("Photos ${timestamp()}")
            withContext(Dispatchers.IO) { PdfTools.imagesToPdf(context, uris, out) }
            refresh()
            onOpen(out)
        }
    }

    val pickWord = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        work("Converting to PDF…") {
            val name = repo.displayName(uri) ?: "Document.docx"
            val out = repo.newFile(name.substringBeforeLast('.'))
            try {
                withContext(Dispatchers.IO) {
                    val kind = WordToPdf.detect(context, uri, name)
                    WordToPdf.convert(context, uri, kind, out)
                }
            } catch (e: Exception) {
                out.delete()
                throw e
            }
            refresh()
            message("Converted to PDF")
            onOpen(out)
        }
    }

    fun runTool(tool: Tool) {
        when (tool) {
            Tool.Scan -> startScan()
            Tool.Import -> importPdfs.launch(arrayOf("application/pdf"))
            Tool.Photos -> pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            Tool.Word -> pickWord.launch(WORD_TYPES)
            Tool.Merge -> onMerge()
            Tool.IdCard -> {
                idFront = null
                showIdIntro = true
            }
            else -> pickFor = tool
        }
    }

    Scaffold(
        containerColor = AppColors.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column {
                Hairline()
                NavigationBar(containerColor = AppColors.Card, tonalElevation = 0.dp) {
                    NavItem("Home", AppIcons.Home, tab == Tab.Home) { tab = Tab.Home }
                    NavItem("Documents", AppIcons.Folder, tab == Tab.Files) { tab = Tab.Files }
                    NavItem("Tools", AppIcons.Grid, tab == Tab.Tools) { tab = Tab.Tools }
                }
            }
        },
        floatingActionButton = {
            if (tab == Tab.Files) {
                ExtendedFloatingActionButton(
                    onClick = { startScan() },
                    containerColor = AppColors.Primary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(Radius.l),
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp),
                    icon = { Icon(AppIcons.Scan, contentDescription = null) },
                    text = { Text("Scan", style = MaterialTheme.typography.labelLarge) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        val bottom = inner.calculateBottomPadding()
        when (tab) {
            Tab.Home -> DashboardTab(
                docs = docs,
                loaded = loaded,
                bottomPadding = bottom,
                onScan = { startScan() },
                onImport = { runTool(Tool.Import) },
                onPhotos = { runTool(Tool.Photos) },
                onTool = { runTool(it) },
                onOpen = { onOpen(it.file) },
                onSeeAllTools = { tab = Tab.Tools },
                onSeeAllFiles = { tab = Tab.Files },
                onSettings = onSettings,
            )
            Tab.Files -> FilesTab(
                docs = docs,
                loaded = loaded,
                bottomPadding = bottom,
                onOpen = { onOpen(it.file) },
                onShare = { shareFile(context, it.file) },
                onRename = { renameTarget = it },
                onDelete = { deleteTarget = it },
                onSettings = onSettings,
            )
            Tab.Tools -> ToolsTab(bottomPadding = bottom, onTool = { runTool(it) })
        }
    }

    if (showIdIntro) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showIdIntro = false },
            containerColor = AppColors.Card,
            icon = { ToolIcon(Tool.IdCard, 48.dp) },
            title = { Text("Scan an ID card") },
            text = {
                Text(
                    "First scan the front, then the back. ScanStack puts both sides on one Letter page, " +
                        "enlarged so they're easy to read, print or share.",
                    color = AppColors.TextSecondary,
                )
            },
            confirmButton = {
                Button(onClick = {
                    showIdIntro = false
                    scanIdSide()
                }, shape = RoundedCornerShape(Radius.m)) { Text("Scan front") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showIdIntro = false }) {
                    Text("Cancel", color = AppColors.TextSecondary)
                }
            },
        )
    }
    if (askForBack) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {},
            containerColor = AppColors.Card,
            icon = { ToolIcon(AppIcons.Check, AppColors.TealDark, 48.dp, AppColors.TealSubtle) },
            title = { Text("Front side done") },
            text = { Text("Now flip the card over and scan the back.", color = AppColors.TextSecondary) },
            confirmButton = {
                Button(onClick = {
                    askForBack = false
                    scanIdSide()
                }, shape = RoundedCornerShape(Radius.m)) { Text("Scan back") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    askForBack = false
                    idFront?.let { buildIdCard(it, null) }
                }) { Text("Front only", color = AppColors.TextSecondary) }
            },
        )
    }
    if (showStorageIntro) {
        StorageIntroDialog(
            onAllow = {
                showStorageIntro = false
                StorageLocation.markAsked(context)
                requestStorage()
            },
            onLater = {
                showStorageIntro = false
                StorageLocation.markAsked(context)
            },
        )
    }
    pickFor?.let { tool ->
        DocPickerDialog(
            tool = tool,
            docs = docs,
            onDismiss = { pickFor = null },
            onPick = { doc ->
                pickFor = null
                onTool(tool, doc.file)
            },
            onImport = {
                pickFor = null
                importFor = tool
                importPdfs.launch(arrayOf("application/pdf"))
            },
        )
    }
    renameTarget?.let { doc ->
        TextInputDialog(
            title = "Rename",
            initial = doc.name,
            label = "Name",
            confirmText = "Rename",
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                renameTarget = null
                work("Renaming…") {
                    withContext(Dispatchers.IO) { repo.rename(doc.file, name) }
                    refresh()
                }
            },
        )
    }
    deleteTarget?.let { doc ->
        ConfirmDialog(
            title = "Delete this document?",
            body = "\"${doc.name}\" will be permanently deleted from ScanStack.",
            confirmText = "Delete",
            onDismiss = { deleteTarget = null },
            onConfirm = {
                deleteTarget = null
                repo.delete(doc.file)
                refresh()
                message("Deleted")
            },
        )
    }
    busy?.let { BusyDialog(it) }
}

// ---------------------------------------------------------------- Home tab

@Composable
private fun DashboardTab(
    docs: List<Doc>,
    loaded: Boolean,
    bottomPadding: Dp,
    onScan: () -> Unit,
    onImport: () -> Unit,
    onPhotos: () -> Unit,
    onTool: (Tool) -> Unit,
    onOpen: (Doc) -> Unit,
    onSeeAllTools: () -> Unit,
    onSeeAllFiles: () -> Unit,
    onSettings: () -> Unit,
) {
    val pages = docs.sumOf { it.pages.coerceAtLeast(0) }
    val storage = docs.sumOf { it.sizeBytes }
    val featured = listOf(Tool.IdCard, Tool.Enhance, Tool.Sign, Tool.Form, Tool.Word, Tool.Watermark, Tool.Merge, Tool.Compress)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding + Space.xl),
    ) {
        // Header
        item {
            Column(
                Modifier
                    .statusBarsPadding()
                    .padding(start = Space.l, end = Space.s, top = Space.s),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.logo_mark), contentDescription = null, modifier = Modifier.size(34.dp))
                    HSpace(10)
                    Text("ScanStack", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppColors.Text)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onSeeAllFiles) {
                        Icon(AppIcons.Search, contentDescription = "Search documents", tint = AppColors.Text)
                    }
                    IconButton(onClick = onSettings) {
                        Icon(AppIcons.Settings, contentDescription = "Settings", tint = AppColors.Text)
                    }
                }
                Spacer(Modifier.height(Space.l))
                Text(greeting(), style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
                Spacer(Modifier.height(2.dp))
                Text(
                    "What would you like to do?",
                    style = MaterialTheme.typography.headlineSmall,
                    color = AppColors.Text,
                    modifier = Modifier.padding(end = Space.s),
                )
            }
        }

        // Primary action
        item {
            ScanCard(Modifier.padding(start = Space.l, end = Space.l, top = 20.dp), onScan)
        }

        // Secondary actions
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.l, vertical = Space.m),
                horizontalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                QuickAction("Import PDF", "From your phone", AppIcons.Import, Modifier.weight(1f), onImport)
                QuickAction("Photos to PDF", "From your gallery", AppIcons.Image, Modifier.weight(1f), onPhotos)
            }
        }

        // Stats
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.l),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                Stat("Documents", "${docs.size}", Modifier.weight(1f))
                Stat("Pages", "$pages", Modifier.weight(1f))
                Stat("Storage", formatSize(storage), Modifier.weight(1f))
            }
        }

        // Tools
        item { SectionTitle("Tools", "See all", onSeeAllTools) }
        item {
            WhiteCard(Modifier.padding(horizontal = Space.l)) {
                Column(Modifier.padding(vertical = Space.s, horizontal = Space.xs)) {
                    featured.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { tool -> ToolButton(tool, Modifier.weight(1f)) { onTool(tool) } }
                        }
                    }
                }
            }
        }

        // Recent
        item { SectionTitle("Recent documents", if (docs.isNotEmpty()) "See all" else null, onSeeAllFiles) }
        item {
            if (loaded && docs.isEmpty()) {
                EmptyDocs(Modifier.padding(horizontal = Space.l), onScan)
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Space.l),
                    horizontalArrangement = Arrangement.spacedBy(Space.m),
                ) {
                    items(docs.take(12), key = { it.file.absolutePath }) { doc -> RecentCard(doc) { onOpen(doc) } }
                }
            }
        }
    }
}

/** The main call to action: solid brand blue, white text, teal scan symbol. */
@Composable
private fun ScanCard(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.l))
            .background(AppColors.Primary)
            .clickable(onClick = onClick)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Scan a document", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Spacer(Modifier.height(Space.xs))
                Text(
                    "Your camera finds the edges, crops and cleans up each page.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.82f),
                )
                Spacer(Modifier.height(Space.l))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(Radius.s))
                        .background(Color.White)
                        .padding(horizontal = 14.dp, vertical = Space.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.Camera, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(18.dp))
                    HSpace(8)
                    Text("Start scanning", style = MaterialTheme.typography.labelLarge, color = AppColors.Primary)
                }
            }
            HSpace(16)
            Box(
                Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(Radius.l))
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Scan, contentDescription = null, tint = AppColors.Teal, modifier = Modifier.size(40.dp))
            }
        }
    }
}

@Composable
private fun QuickAction(title: String, subtitle: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    WhiteCard(modifier, onClick = onClick) {
        Row(Modifier.padding(Space.m), verticalAlignment = Alignment.CenterVertically) {
            ToolIcon(icon, size = 40.dp)
            HSpace(10)
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = AppColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    WhiteCard(modifier) {
        Column(Modifier.padding(horizontal = Space.m, vertical = Space.m)) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = AppColors.Text, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelMedium, color = AppColors.TextSecondary)
        }
    }
}

@Composable
private fun RecentCard(doc: Doc, onClick: () -> Unit) {
    WhiteCard(Modifier.width(132.dp), onClick = onClick) {
        Column(Modifier.padding(Space.s)) {
            Thumbnail(
                doc,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.78f)
                    .clip(RoundedCornerShape(Radius.s))
                    .border(1.dp, AppColors.Border, RoundedCornerShape(Radius.s)),
                widthPx = 320,
            )
            Spacer(Modifier.height(Space.s))
            Text(
                doc.name,
                style = MaterialTheme.typography.labelLarge,
                color = AppColors.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
            Text(
                if (doc.pages < 0) "Locked" else "${doc.pages} ${if (doc.pages == 1) "page" else "pages"} · ${formatSize(doc.sizeBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, action: String?, onAction: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = Space.s, top = Space.xl, bottom = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = AppColors.Text, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = AppColors.Primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.s))
                    .clickable(onClick = onAction)
                    .padding(horizontal = Space.m, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun EmptyDocs(modifier: Modifier, onScan: () -> Unit) {
    WhiteCard(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ToolIcon(AppIcons.Document, AppColors.TealDark, 56.dp, AppColors.TealSubtle)
            Spacer(Modifier.height(Space.m))
            Text("No documents yet", style = MaterialTheme.typography.titleMedium, color = AppColors.Text)
            Spacer(Modifier.height(Space.xs))
            Text(
                "Scan a page with your camera or import a PDF to get started.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Space.l))
            Button(onClick = onScan, shape = RoundedCornerShape(Radius.m)) {
                Icon(AppIcons.Scan, contentDescription = null, modifier = Modifier.size(18.dp))
                HSpace(8)
                Text("Scan a document")
            }
        }
    }
}

// ---------------------------------------------------------------- Documents tab

@Composable
private fun FilesTab(
    docs: List<Doc>,
    loaded: Boolean,
    bottomPadding: Dp,
    onOpen: (Doc) -> Unit,
    onShare: (Doc) -> Unit,
    onRename: (Doc) -> Unit,
    onDelete: (Doc) -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val location = remember(docs) { StorageLocation.displayPath(StorageLocation.activeFolder(context)) }
    val isPublic = remember(docs) { StorageLocation.isPublic(context) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(SortBy.Recent) }
    val shown = docs
        .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
        .let { list ->
            when (sort) {
                SortBy.Recent -> list.sortedByDescending { it.modified }
                SortBy.Name -> list.sortedBy { it.name.lowercase() }
                SortBy.Size -> list.sortedByDescending { it.sizeBytes }
            }
        }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding + 96.dp),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        item {
            Column(
                Modifier
                    .statusBarsPadding()
                    .padding(start = Space.l, end = Space.l, top = 20.dp),
            ) {
                Text("Documents", style = MaterialTheme.typography.headlineSmall, color = AppColors.Text)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${docs.size} ${if (docs.size == 1) "document" else "documents"} · ${formatSize(docs.sumOf { it.sizeBytes })}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary,
                )
                Spacer(Modifier.height(Space.s))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(Radius.s))
                        .clickable(onClick = onSettings)
                        .padding(vertical = Space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.Folder, contentDescription = null, tint = AppColors.TealDark, modifier = Modifier.size(16.dp))
                    HSpace(6)
                    Text(
                        if (isPublic) location else "Kept inside the app · tap to save to Internal storage/ScanStack",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.TealDark,
                    )
                }
                Spacer(Modifier.height(Space.m))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search documents", color = AppColors.TextMuted) },
                    leadingIcon = { Icon(AppIcons.Search, contentDescription = null, tint = AppColors.TextSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(Radius.m),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = AppColors.Card,
                        unfocusedContainerColor = AppColors.Card,
                        focusedBorderColor = AppColors.Primary,
                        unfocusedBorderColor = AppColors.Border,
                        focusedTextColor = AppColors.Text,
                        unfocusedTextColor = AppColors.Text,
                        cursorColor = AppColors.Primary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Space.m))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    Text("Sort", style = MaterialTheme.typography.labelMedium, color = AppColors.TextSecondary)
                    SortBy.entries.forEach { option ->
                        FilterChip(
                            selected = sort == option,
                            onClick = { sort = option },
                            label = { Text(option.label) },
                            shape = RoundedCornerShape(Radius.s),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = AppColors.Card,
                                labelColor = AppColors.TextSecondary,
                                selectedContainerColor = AppColors.TealSubtle,
                                selectedLabelColor = AppColors.TealDark,
                            ),
                        )
                    }
                }
            }
        }
        if (loaded && shown.isEmpty()) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.xl, vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ToolIcon(AppIcons.Folder, size = 56.dp)
                    Spacer(Modifier.height(Space.m))
                    Text(
                        if (docs.isEmpty()) "No documents yet" else "No matches",
                        style = MaterialTheme.typography.titleMedium,
                        color = AppColors.Text,
                    )
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        if (docs.isEmpty()) "Tap Scan to capture your first document." else "Nothing is called \"$query\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        items(shown, key = { it.file.absolutePath }) { doc ->
            DocRow(
                doc = doc,
                modifier = Modifier.padding(horizontal = Space.l),
                onClick = { onOpen(doc) },
                onShare = { onShare(doc) },
                onRename = { onRename(doc) },
                onDelete = { onDelete(doc) },
            )
        }
    }
}

@Composable
private fun DocRow(
    doc: Doc,
    modifier: Modifier,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    WhiteCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(start = Space.m, top = Space.m, bottom = Space.m), verticalAlignment = Alignment.CenterVertically) {
            Thumbnail(
                doc,
                Modifier
                    .size(width = 46.dp, height = 60.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, AppColors.Border, RoundedCornerShape(6.dp)),
            )
            HSpace(14)
            Column(Modifier.weight(1f)) {
                Text(doc.name, style = MaterialTheme.typography.titleSmall, color = AppColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(Space.xs))
                Text(docInfo(doc), style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(AppIcons.More, contentDescription = "More options", tint = AppColors.TextSecondary)
                }
                DropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                    containerColor = AppColors.Card,
                    shape = RoundedCornerShape(Radius.m),
                ) {
                    MenuItem("Share", AppIcons.Share) {
                        menu = false
                        onShare()
                    }
                    MenuItem("Rename", AppIcons.Edit) {
                        menu = false
                        onRename()
                    }
                    MenuItem("Delete", AppIcons.Trash, danger = true) {
                        menu = false
                        onDelete()
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Tools tab

@Composable
private fun ToolsTab(bottomPadding: Dp, onTool: (Tool) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding + Space.xl),
    ) {
        item {
            Column(
                Modifier
                    .statusBarsPadding()
                    .padding(start = Space.l, end = Space.l, top = 20.dp),
            ) {
                Text("Tools", style = MaterialTheme.typography.headlineSmall, color = AppColors.Text)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Everything runs on your phone. Nothing is uploaded.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary,
                )
            }
        }
        ToolGroup.entries.forEach { group ->
            item {
                Text(
                    group.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = AppColors.TextSecondary,
                    modifier = Modifier.padding(start = 20.dp, top = Space.xl, bottom = Space.s),
                )
            }
            item {
                WhiteCard(Modifier.padding(horizontal = Space.l)) {
                    val tools = Tool.entries.filter { it.group == group }
                    tools.forEachIndexed { i, tool ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onTool(tool) }
                                .padding(horizontal = Space.m, vertical = Space.m),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ToolIcon(tool, 42.dp)
                            HSpace(14)
                            Column(Modifier.weight(1f)) {
                                Text(tool.label, style = MaterialTheme.typography.titleSmall, color = AppColors.Text)
                                Text(tool.description, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                            }
                            Icon(AppIcons.ChevronRight, contentDescription = null, tint = AppColors.TextMuted, modifier = Modifier.size(20.dp))
                        }
                        if (i < tools.lastIndex) Hairline(Modifier.padding(start = 68.dp))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- shared pieces

/** White card with a hairline border and no shadow. */
@Composable
fun WhiteCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(Radius.l)
    val colors = CardDefaults.cardColors(containerColor = AppColors.Card)
    val border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Border)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors, border = border) { content() }
    } else {
        Card(modifier = modifier, shape = shape, colors = colors, border = border) { content() }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = AppColors.Primary,
            selectedTextColor = AppColors.Primary,
            indicatorColor = AppColors.BlueSubtle,
            unselectedIconColor = AppColors.TextSecondary,
            unselectedTextColor = AppColors.TextSecondary,
        ),
    )
}

private fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
}
