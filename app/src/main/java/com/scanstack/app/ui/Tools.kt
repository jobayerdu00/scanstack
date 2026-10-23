package com.scanstack.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

/**
 * Every tool in the app. [needsDoc] tools first ask which document to use.
 * [accent] tools use the teal highlight; all others use the brand blue.
 */
enum class Tool(
    val label: String,
    val description: String,
    val icon: ImageVector,
    val group: ToolGroup,
    val needsDoc: Boolean,
    val accent: Boolean = false,
) {
    Scan("Scan", "Use the camera, auto-crop and enhance", AppIcons.Scan, ToolGroup.Create, false, accent = true),
    Import("Import PDF", "Add PDFs from your phone", AppIcons.Import, ToolGroup.Create, false),
    Photos("Photos to PDF", "Turn pictures into a PDF", AppIcons.Image, ToolGroup.Create, false),
    IdCard("ID card", "Scan both sides onto one page", AppIcons.IdCard, ToolGroup.Create, false, accent = true),
    Word("Word to PDF", "Convert .docx documents to PDF", AppIcons.Convert, ToolGroup.Create, false),
    Sign("Fill & Sign", "Add your signature, text, dates and ticks", AppIcons.Signature, ToolGroup.Edit, true),
    Form("Fill form", "Type into a PDF's form fields", AppIcons.Form, ToolGroup.Edit, true),
    Watermark("Watermark", "Stamp text or a logo on every page", AppIcons.Watermark, ToolGroup.Edit, true),
    Merge("Merge", "Combine several PDFs into one", AppIcons.Merge, ToolGroup.Edit, false),
    Extract("Extract pages", "Copy chosen pages into a new PDF", AppIcons.Scissors, ToolGroup.Edit, true),
    Enhance("Enhance", "Brighten pages and boost color", AppIcons.Enhance, ToolGroup.Optimize, true, accent = true),
    Compress("Compress", "Make files smaller to send", AppIcons.Compress, ToolGroup.Optimize, true),
    Text("Get text", "Copy text from scans and PDFs", AppIcons.Ocr, ToolGroup.Optimize, true),
    Protect("Password", "Add or remove a password", AppIcons.Lock, ToolGroup.Optimize, true);

    val tint: Color get() = if (accent) AppColors.TealDark else AppColors.Primary
    val tile: Color get() = if (accent) AppColors.TealSubtle else AppColors.BlueSubtle
}

/** Softly tinted square with an icon. */
@Composable
fun ToolIcon(tool: Tool, size: Dp = 48.dp) = ToolIcon(tool.icon, tool.tint, size, tool.tile)

@Composable
fun ToolIcon(icon: ImageVector, tint: Color = AppColors.Primary, size: Dp = 48.dp, tile: Color = AppColors.BlueSubtle) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(tile),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** Grid cell: icon above a short label. */
@Composable
fun ToolButton(tool: Tool, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(Radius.m))
            .clickable(onClick = onClick)
            .padding(vertical = Space.s, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ToolIcon(tool)
        Spacer(Modifier.height(Space.s))
        Text(
            tool.label,
            style = MaterialTheme.typography.labelMedium,
            color = AppColors.Text,
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
        containerColor = AppColors.Card,
        icon = { ToolIcon(tool, 44.dp) },
        title = { Text(tool.label) },
        text = {
            if (usable.isEmpty()) {
                Text(
                    "Choose a PDF from your phone to get started.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary,
                )
            } else {
                Column {
                    Text(
                        "Choose a document",
                        style = MaterialTheme.typography.labelLarge,
                        color = AppColors.TextSecondary,
                    )
                    Spacer(Modifier.height(Space.s))
                    LazyColumn(Modifier.heightIn(max = 340.dp)) {
                        items(usable, key = { it.file.absolutePath }) { doc ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(Radius.m))
                                    .clickable { onPick(doc) }
                                    .padding(vertical = Space.s, horizontal = Space.xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Thumbnail(
                                    doc,
                                    Modifier
                                        .size(width = 36.dp, height = 48.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .border(1.dp, AppColors.Border, RoundedCornerShape(6.dp)),
                                )
                                HSpace(12)
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        doc.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = AppColors.Text,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        docInfo(doc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AppColors.TextSecondary,
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = AppColors.TextSecondary) } },
    )
}
