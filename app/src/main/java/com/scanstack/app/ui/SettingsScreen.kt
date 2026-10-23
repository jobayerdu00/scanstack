@file:OptIn(ExperimentalMaterial3Api::class)

package com.scanstack.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.scanstack.app.BuildConfig
import com.scanstack.app.data.DocRepository
import com.scanstack.app.data.StorageLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Asks for storage access in the right way for the Android version:
 * "All files access" screen on Android 11+, the normal permission prompt on 8–10.
 * [onResult] gets whether access is now granted.
 */
@Composable
fun rememberStorageAccessRequest(onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        onResult(StorageLocation.hasAccess(context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onResult(granted)
    }
    return {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                settingsLauncher.launch(StorageLocation.allFilesAccessIntent(context))
            } catch (e: Exception) {
                settingsLauncher.launch(StorageLocation.allFilesAccessFallbackIntent())
            }
        } else {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
}

/** First-run prompt explaining why ScanStack asks for storage access. */
@Composable
fun StorageIntroDialog(onAllow: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        containerColor = AppColors.Card,
        icon = { ToolIcon(AppIcons.Folder, AppColors.TealDark, 48.dp, AppColors.TealSubtle) },
        title = { Text("Save documents to your phone") },
        text = {
            Text(
                "ScanStack keeps your PDFs in Internal storage/ScanStack so you can find them in any file manager " +
                    "or on your computer. Android will ask you to allow file access for ScanStack.",
                color = AppColors.TextSecondary,
            )
        },
        confirmButton = { Button(onClick = onAllow, shape = RoundedCornerShape(Radius.m)) { Text("Allow") } },
        dismissButton = { TextButton(onClick = onLater) { Text("Not now", color = AppColors.TextSecondary) } },
    )
}

@Composable
fun SettingsScreen(
    repo: DocRepository,
    snackbar: SnackbarHostState,
    message: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf<String?>(null) }

    // Re-read on every change so the screen always shows the real state
    val hasAccess = remember(refresh) { StorageLocation.hasAccess(context) }
    val chosen = remember(refresh) { StorageLocation.chosenFolder(context) }
    val active = remember(refresh) { StorageLocation.activeFolder(context) }
    val isDefault = chosen.absoluteFile == StorageLocation.defaultFolder().absoluteFile
    val docCount = remember(refresh) {
        active.listFiles { f -> f.isFile && f.extension.equals("pdf", true) }?.size ?: 0
    }

    /** Moves documents from the current folder to [target] and makes it the save location. */
    fun switchTo(target: File, isDefaultTarget: Boolean) {
        scope.launch {
            busy = "Moving documents…"
            try {
                val from = StorageLocation.activeFolder(context)
                val moved = withContext(Dispatchers.IO) {
                    target.mkdirs()
                    if (!target.isDirectory || !target.canWrite()) throw java.io.IOException("ScanStack can't save in that folder")
                    val count = repo.moveAll(from, target)
                    if (isDefaultTarget) StorageLocation.resetToDefault(context) else StorageLocation.setChosenFolder(context, target)
                    count
                }
                refresh++
                message(
                    "Saving to ${StorageLocation.displayPath(target)}" +
                        (if (moved > 0) " · moved $moved ${if (moved == 1) "document" else "documents"}" else ""),
                )
            } catch (e: Exception) {
                message(friendlyError(e))
            } finally {
                busy = null
            }
        }
    }

    val requestAccess = rememberStorageAccessRequest { granted ->
        if (granted) {
            scope.launch {
                busy = "Moving documents…"
                val moved = withContext(Dispatchers.IO) {
                    repo.moveAll(StorageLocation.privateFolder(context), StorageLocation.activeFolder(context))
                }
                busy = null
                refresh++
                message(
                    "Documents are now saved in ${StorageLocation.displayPath(StorageLocation.chosenFolder(context))}" +
                        (if (moved > 0) " ($moved moved)" else ""),
                )
            }
        } else {
            refresh++
            message("Storage access wasn't allowed. Documents stay inside the app.")
        }
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val folder = StorageLocation.folderFromTreeUri(uri)
        if (folder == null) message("Please choose a folder on your phone's storage") else switchTo(folder, false)
    }

    Scaffold(
        containerColor = AppColors.Background,
        topBar = {
            TopAppBar(
                colors = appBarColors(),
                title = { Text("Settings") },
                navigationIcon = { BackButton(onBack) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.l, vertical = Space.s),
        ) {
            SectionLabel("Storage")
            WhiteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Space.l)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ToolIcon(AppIcons.Folder, size = 44.dp)
                        HSpace(12)
                        Column(Modifier.weight(1f)) {
                            Text("Save location", style = MaterialTheme.typography.labelMedium, color = AppColors.TextSecondary)
                            Text(
                                StorageLocation.displayPath(chosen),
                                style = MaterialTheme.typography.titleMedium,
                                color = AppColors.Text,
                            )
                        }
                    }
                    Spacer(Modifier.height(Space.m))
                    StatusLine(
                        ok = hasAccess,
                        text = if (hasAccess) {
                            "$docCount ${if (docCount == 1) "document" else "documents"} saved here"
                        } else {
                            "Storage access is off, so documents are kept inside the app for now"
                        },
                    )
                    Spacer(Modifier.height(Space.l))
                    if (!hasAccess) {
                        Button(
                            onClick = requestAccess,
                            shape = RoundedCornerShape(Radius.m),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Allow storage access") }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                            Button(
                                onClick = { pickFolder.launch(null) },
                                shape = RoundedCornerShape(Radius.m),
                                modifier = Modifier.weight(1f),
                            ) { Text("Change folder") }
                            OutlinedButton(
                                onClick = { switchTo(StorageLocation.defaultFolder(), true) },
                                enabled = !isDefault,
                                shape = RoundedCornerShape(Radius.m),
                                modifier = Modifier.weight(1f),
                            ) { Text("Use default") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(Space.s))
            Row(Modifier.padding(horizontal = Space.xs), verticalAlignment = Alignment.Top) {
                Icon(AppIcons.Info, contentDescription = null, tint = AppColors.TextMuted, modifier = Modifier.size(16.dp))
                HSpace(8)
                Text(
                    "Find your PDFs in any file manager under ${StorageLocation.displayPath(chosen)}. " +
                        "When you change the folder, your documents move with it. The default is Internal storage/ScanStack.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextSecondary,
                )
            }

            SectionLabel("About")
            WhiteCard(Modifier.fillMaxWidth()) {
                Column {
                    InfoRow("Version", BuildConfig.VERSION_NAME)
                    Hairline(Modifier.padding(start = Space.l))
                    InfoRow("Privacy", "Everything runs on your phone. Nothing is uploaded.")
                    Hairline(Modifier.padding(start = Space.l))
                    InfoRow("Fonts", "Inter, Great Vibes, Allura, Sacramento, Mrs Saint Delafield, Dancing Script, Caveat · SIL Open Font License")
                }
            }
            Spacer(Modifier.height(Space.xl))
        }
    }

    busy?.let { BusyDialog(it) }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = AppColors.TextSecondary,
        modifier = Modifier.padding(start = Space.xs, top = Space.l, bottom = Space.s),
    )
}

@Composable
private fun StatusLine(ok: Boolean, text: String) {
    val color = if (ok) AppColors.Success else AppColors.Warning
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ok) AppIcons.Check else AppIcons.Info, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        HSpace(8)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = if (ok) AppColors.Text else Color(0xFF92400E))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(Modifier.padding(horizontal = Space.l, vertical = Space.m)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = AppColors.TextSecondary)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = AppColors.Text)
    }
}
