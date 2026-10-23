@file:OptIn(ExperimentalMaterial3Api::class)

package com.scanstack.app.ui

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.scanstack.app.data.Doc
import com.scanstack.app.data.DocRepository
import com.scanstack.app.pdf.PdfTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var docs by remember { mutableStateOf<List<Doc>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<Doc?>(null) }
    var deleteTarget by remember { mutableStateOf<Doc?>(null) }

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

    // ----- Camera scanner (Google's on-device scanner: auto edges, crop, filters) -----
    val scanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
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

    // ----- Import PDFs from the phone -----
    val importPdfs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
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
                files.size == 1 -> onOpen(files[0])
                else -> message("Imported ${files.size} PDFs")
            }
        }
    }

    // ----- Photos to PDF -----
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        work("Creating PDF…") {
            val out = repo.newFile("Photos ${timestamp()}")
            withContext(Dispatchers.IO) { PdfTools.imagesToPdf(context, uris, out) }
            refresh()
            onOpen(out)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("ScanStack", fontWeight = FontWeight.SemiBold) })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { startScan() },
                icon = { Icon(Icons.Outlined.DocumentScanner, contentDescription = null) },
                text = { Text("Scan") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ToolTile("Import PDF", Icons.Outlined.FileOpen, Modifier.weight(1f)) {
                        importPdfs.launch(arrayOf("application/pdf"))
                    }
                    ToolTile("Photos to PDF", Icons.Outlined.PhotoLibrary, Modifier.weight(1f)) {
                        pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    ToolTile("Merge PDFs", Icons.Outlined.MergeType, Modifier.weight(1f), onClick = onMerge)
                }
            }
            item {
                Text(
                    "My documents",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                )
            }
            if (loaded && docs.isEmpty()) {
                item { EmptyState() }
            }
            items(docs, key = { it.file.absolutePath }) { doc ->
                DocRow(
                    doc = doc,
                    onClick = { onOpen(doc.file) },
                    onShare = { shareFile(context, doc.file) },
                    onRename = { renameTarget = doc },
                    onDelete = { deleteTarget = doc },
                )
            }
        }
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

@Composable
private fun ToolTile(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun DocRow(
    doc: Doc,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumbnail(
                doc,
                Modifier
                    .size(width = 52.dp, height = 68.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )
            HSpace(14)
            Column(Modifier.weight(1f)) {
                Text(
                    doc.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    docInfo(doc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                }
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

@Composable
private fun EmptyState() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Outlined.DocumentScanner,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text("No documents yet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Tap Scan to photograph a document, or import a PDF from your phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
