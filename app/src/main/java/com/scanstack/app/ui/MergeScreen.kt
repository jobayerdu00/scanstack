@file:OptIn(ExperimentalMaterial3Api::class)

package com.scanstack.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scanstack.app.data.Doc
import com.scanstack.app.data.DocRepository
import com.scanstack.app.pdf.PdfTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun MergeScreen(
    repo: DocRepository,
    snackbar: SnackbarHostState,
    message: (String) -> Unit,
    onBack: () -> Unit,
    onDone: (File) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var docs by remember { mutableStateOf<List<Doc>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<File>() }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        docs = withContext(Dispatchers.IO) { repo.list().filter { it.pages > 0 } }
        loaded = true
    }

    fun merge() {
        scope.launch {
            busy = true
            try {
                val out = repo.newFile("Merged ${timestamp()}")
                withContext(Dispatchers.IO) { PdfTools.merge(selected.toList(), out) }
                onDone(out)
            } catch (e: Exception) {
                message(friendlyError(e))
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = appBarColors(),
                title = { Text("Merge PDFs") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.Back, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = AppColors.Card, modifier = Modifier.topHairline()) {
                Button(
                    onClick = { merge() },
                    enabled = selected.size >= 2 && !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                ) {
                    Text(if (selected.size < 2) "Select at least 2 files" else "Merge ${selected.size} files")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    if (loaded && docs.size < 2) {
                        "You need at least two documents in ScanStack to merge. Scan or import more first."
                    } else {
                        "Tap documents in the order you want them combined."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(docs, key = { it.file.absolutePath }) { doc ->
                val order = selected.indexOf(doc.file)
                val isSelected = order >= 0
                Card(
                    onClick = { if (isSelected) selected.remove(doc.file) else selected.add(doc.file) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) AppColors.TealSubtle else AppColors.Card,
                    ),
                    border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) AppColors.Teal else AppColors.Border),
                    shape = RoundedCornerShape(Radius.l),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Thumbnail(
                            doc,
                            Modifier
                                .size(width = 44.dp, height = 58.dp)
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
                        HSpace(8)
                        OrderBadge(if (isSelected) order + 1 else null)
                    }
                }
            }
        }
    }

    if (busy) BusyDialog("Merging…")
}

@Composable
private fun OrderBadge(number: Int?) {
    val primary = AppColors.Teal
    if (number == null) {
        Box(
            Modifier
                .size(28.dp)
                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
        )
    } else {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$number",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
