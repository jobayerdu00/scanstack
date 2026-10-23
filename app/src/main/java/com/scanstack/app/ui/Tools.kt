package com.scanstack.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scanstack.app.data.Doc

enum class ToolGroup(val title: String) {
    Create("Create PDF"),
    Edit("Edit & sign"),
    Optimize("Optimize & protect"),
}

/** Every tool in the app. [needsDoc] tools first ask which document to use. */
enum class Tool(
    val label: String,
    val description: String,
    val icon: ImageVector,
    val tint: Color,
    val group: ToolGroup,
    val needsDoc: Boolean,
) {
    Scan("Scan", "Use the camera, auto-crop and enhance", Icons.Outlined.CameraAlt, Brand.Teal, ToolGroup.Create, false),
    Import("Import PDF", "Add PDFs from your phone", Icons.Outlined.FileOpen, Brand.Blue, ToolGroup.Create, false),
    Photos("Photos to PDF", "Turn pictures into a PDF", Icons.Outlined.PhotoLibrary, Brand.Violet, ToolGroup.Create, false),
    Word("Word to PDF", "Convert .docx documents to PDF", Icons.Outlined.Description, Brand.Indigo, ToolGroup.Create, false),
    Sign("Fill & Sign", "Add your signature, text, dates and ticks", Icons.Outlined.Draw, Brand.Pink, ToolGroup.Edit, true),
    Form("Fill form", "Type into a PDF's form fields", Icons.Outlined.EditNote, Brand.Green, ToolGroup.Edit, true),
    Merge("Merge", "Combine several PDFs into one", Icons.Outlined.Layers, Brand.Orange, ToolGroup.Edit, false),
    Extract("Extract pages", "Copy chosen pages into a new PDF", Icons.Outlined.ContentCut, Brand.Brown, ToolGroup.Edit, true),
    Compress("Compress", "Make files smaller to send", Icons.Outlined.Compress, Brand.Amber, ToolGroup.Optimize, true),
    Text("Get text", "Copy text from scans and PDFs", Icons.Outlined.TextFields, Brand.Cyan, ToolGroup.Optimize, true),
    Protect("Password", "Add or remove a password", Icons.Outlined.Lock, Brand.Slate, ToolGroup.Optimize, true),
}

/** Rounded, softly tinted square with the tool's icon. */
@Composable
fun ToolIcon(tool: Tool, size: Dp = 52.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(tool.tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(tool.icon, contentDescription = null, tint = tool.tint, modifier = Modifier.size(size * 0.48f))
    }
}

/** Grid cell: icon above a short label. */
@Composable
fun ToolButton(tool: Tool, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ToolIcon(tool)
        Spacer(Modifier.height(6.dp))
        Text(
            tool.label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "Which document?" chooser for tools that work on an existing PDF. */
@Composable
fun DocPickerDialog(
    tool: Tool,
    docs: List<Doc>,
    onDismiss: () -> Unit,
    onPick: (Doc) -> Unit,
    onImport: () -> Unit,
) {
    val usable = if (tool == Tool.Protect) docs else docs.filter { it.pages > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { ToolIcon(tool, 44.dp) },
        title = { Text(tool.label) },
        text = {
            if (usable.isEmpty()) {
                Text(
                    "Choose a PDF from your phone to get started.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Column {
                    Text(
                        "Choose a document",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max = 340.dp)) {
                        items(usable, key = { it.file.absolutePath }) { doc ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onPick(doc) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Thumbnail(
                                    doc,
                                    Modifier
                                        .size(width = 36.dp, height = 48.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                )
                                HSpace(12)
                                Column(Modifier.weight(1f)) {
                                    Text(doc.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        docInfo(doc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onImport) { Text("From phone") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
