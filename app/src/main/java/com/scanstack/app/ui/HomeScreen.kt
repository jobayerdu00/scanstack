@file:OptIn(ExperimentalMaterial3Api::class)

package com.scanstack.app.ui

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.scanstack.app.data.Doc
import com.scanstack.app.data.DocRepository
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

    LightStatusBarIcons(false)

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
            else -> pickFor = tool
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
                NavItem("Home", Icons.Filled.Home, Icons.Outlined.Home, tab == Tab.Home) { tab = Tab.Home }
                NavItem("Files", Icons.Filled.Folder, Icons.Outlined.Folder, tab == Tab.Files) { tab = Tab.Files }
                NavItem("Tools", Icons.Filled.GridView, Icons.Outlined.GridView, tab == Tab.Tools) { tab = Tab.Tools }
            }
        },
        floatingActionButton = {
            if (tab != Tab.Tools) {
                FloatingActionButton(
                    onClick = { startScan() },
                    containerColor = Brand.Orange,
                    contentColor = Color.White,
                    shape = CircleShape,
                ) { Icon(Icons.Outlined.CameraAlt, contentDescription = "Scan") }
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
                onTool = { runTool(it) },
                onOpen = { onOpen(it.file) },
                onSeeAllTools = { tab = Tab.Tools },
                onSeeAllFiles = { tab = Tab.Files },
            )
            Tab.Files -> FilesTab(
                docs = docs,
                loaded = loaded,
                bottomPadding = bottom,
                onOpen = { onOpen(it.file) },
                onShare = { shareFile(context, it.file) },
                onRename = { renameTarget = it },
                onDelete = { deleteTarget = it },
            )
            Tab.Tools -> ToolsTab(bottomPadding = bottom, onTool = { runTool(it) })
        }
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
    bottomPadding: androidx.compose.ui.unit.Dp,
    onScan: () -> Unit,
    onImport: () -> Unit,
    onTool: (Tool) -> Unit,
    onOpen: (Doc) -> Unit,
    onSeeAllTools: () -> Unit,
    onSeeAllFiles: () -> Unit,
) {
    val pages = docs.sumOf { it.pages.coerceAtLeast(0) }
    val storage = docs.sumOf { it.sizeBytes }
    val featured = listOf(Tool.Sign, Tool.Form, Tool.Word, Tool.Photos, Tool.Merge, Tool.Compress, Tool.Text, Tool.Protect)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = bottomPadding + 88.dp),
    ) {
        item {
            HeaderBox(tall = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppMark()
                    HSpace(10)
                    Text(
                        "ScanStack",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onSeeAllFiles) {
                        Icon(Icons.Outlined.Search, contentDescription = "Search files", tint = Color.White)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(greeting(), color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(2.dp))
                Text(
                    "All your documents in one place",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatPill("Documents", "${docs.size}", Icons.Outlined.Description, Modifier.weight(1f))
                    StatPill("Pages", "$pages", Icons.Outlined.AutoStories, Modifier.weight(1f))
                    StatPill("Storage", formatSize(storage), Icons.Outlined.Storage, Modifier.weight(1f))
                }
            }
        }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HeroCard("Scan", "Camera · auto-crop", Icons.Outlined.DocumentScanner, Brand.scanCard, Modifier.weight(1f), onScan)
                HeroCard("Import PDF", "From your phone", Icons.Outlined.FileOpen, Brand.importCard, Modifier.weight(1f), onImport)
            }
        }

        item { SectionTitle("Tools", "See all", onSeeAllTools) }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(Modifier.padding(vertical = 10.dp, horizontal = 6.dp)) {
                    featured.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { tool -> ToolButton(tool, Modifier.weight(1f)) { onTool(tool) } }
                        }
                    }
                }
            }
        }

        item { SectionTitle("Recent", if (docs.isNotEmpty()) "See all" else null, onSeeAllFiles) }
        item {
            if (loaded && docs.isEmpty()) {
                EmptyDocs(Modifier.padding(horizontal = 16.dp), onScan)
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(docs.take(12), key = { it.file.absolutePath }) { doc -> RecentCard(doc) { onOpen(doc) } }
                }
            }
        }
    }
}

@Composable
private fun RecentCard(doc: Doc, onClick: () -> Unit) {
    Column(
        Modifier
            .width(124.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Card(
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
        ) {
            Thumbnail(
                doc,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.76f),
                widthPx = 320,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(doc.name, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            if (doc.pages < 0) "Locked" else "${doc.pages} ${if (doc.pages == 1) "page" else "pages"} · ${formatSize(doc.sizeBytes)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HeroCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    brush: Brush,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(118.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(brush)
            .clickable(onClick = onClick),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color.White.copy(alpha = 0.12f), radius = size.height * 0.7f, center = Offset(size.width * 1.02f, size.height * 0.05f))
        }
        Column(Modifier.padding(16.dp)) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, icon: ImageVector, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.13f))
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(8.dp))
        Text(value, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SectionTitle(title: String, action: String?, onAction: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 22.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun EmptyDocs(modifier: Modifier, onScan: () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ToolIcon(Tool.Scan, 60.dp)
            Spacer(Modifier.height(12.dp))
            Text("No documents yet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Scan a page with your camera or import a PDF to get started.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            androidx.compose.material3.Button(onClick = onScan) { Text("Scan a document") }
        }
    }
}

// ---------------------------------------------------------------- Files tab

@Composable
private fun FilesTab(
    docs: List<Doc>,
    loaded: Boolean,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onOpen: (Doc) -> Unit,
    onShare: (Doc) -> Unit,
    onRename: (Doc) -> Unit,
    onDelete: (Doc) -> Unit,
) {
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
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = bottomPadding + 88.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            HeaderBox(tall = false) {
                Text("My files", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${docs.size} ${if (docs.size == 1) "document" else "documents"} · ${formatSize(docs.sumOf { it.sizeBytes })}",
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(14.dp))
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search documents") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedTextColor = Color(0xFF111827),
                        unfocusedTextColor = Color(0xFF111827),
                        focusedPlaceholderColor = Color(0xFF6B7280),
                        unfocusedPlaceholderColor = Color(0xFF6B7280),
                        focusedLeadingIconColor = Color(0xFF6B7280),
                        unfocusedLeadingIconColor = Color(0xFF6B7280),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Brand.Teal,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            Row(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SortBy.entries.forEach { option ->
                    FilterChip(
                        selected = sort == option,
                        onClick = { sort = option },
                        label = { Text(option.label) },
                    )
                }
            }
        }
        if (loaded && shown.isEmpty()) {
            item {
                Text(
                    if (docs.isEmpty()) "No documents yet. Tap the camera button to scan one." else "Nothing matches \"$query\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                )
            }
        }
        items(shown, key = { it.file.absolutePath }) { doc ->
            DocRow(
                doc = doc,
                modifier = Modifier.padding(horizontal = 16.dp),
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
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumbnail(
                doc,
                Modifier
                    .size(width = 50.dp, height = 66.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            HSpace(14)
            Column(Modifier.weight(1f)) {
                Text(doc.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(docInfo(doc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More options") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    MenuItem("Share", Icons.Outlined.Share) {
                        menu = false
                        onShare()
                    }
                    MenuItem("Rename", Icons.Outlined.Edit) {
                        menu = false
                        onRename()
                    }
                    MenuItem("Delete", Icons.Outlined.Delete) {
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
private fun ToolsTab(bottomPadding: androidx.compose.ui.unit.Dp, onTool: (Tool) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = bottomPadding + 24.dp),
    ) {
        item {
            HeaderBox(tall = false) {
                Text("All tools", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Everything runs on your phone. Nothing is uploaded.",
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        ToolGroup.entries.forEach { group ->
            item {
                Text(
                    group.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 8.dp),
                )
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Tool.entries.filter { it.group == group }.forEach { tool ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onTool(tool) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ToolIcon(tool, 46.dp)
                            HSpace(14)
                            Column(Modifier.weight(1f)) {
                                Text(tool.label, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    tool.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- shared pieces

@Composable
private fun HeaderBox(tall: Boolean, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp))
            .background(Brand.header),
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Color.White.copy(alpha = 0.06f), radius = size.width * 0.42f, center = Offset(size.width * 0.98f, 0f))
            drawCircle(Color.White.copy(alpha = 0.05f), radius = size.width * 0.22f, center = Offset(size.width * 0.08f, size.height))
        }
        Column(
            Modifier
                .statusBarsPadding()
                .padding(start = 20.dp, end = 12.dp, top = if (tall) 8.dp else 20.dp, bottom = if (tall) 24.dp else 22.dp)
                .padding(end = 8.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun AppMark() {
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.DocumentScanner, contentDescription = null, tint = Brand.Amber, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(
    label: String,
    selectedIcon: ImageVector,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(if (selected) selectedIcon else icon, contentDescription = null) },
        label = { Text(label) },
    )
}

/** The dashboard's dark header needs white status bar icons; restore on leaving. */
@Composable
fun LightStatusBarIcons(dark: Boolean) {
    val view = LocalView.current
    val systemDark = isSystemInDarkTheme()
    DisposableEffect(dark, systemDark) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = dark
        onDispose { controller?.isAppearanceLightStatusBars = !systemDark }
    }
}

private fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
}
