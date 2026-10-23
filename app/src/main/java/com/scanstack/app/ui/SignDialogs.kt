@file:OptIn(ExperimentalMaterial3Api::class)

package com.scanstack.app.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.scanstack.app.pdf.ImageTools
import com.scanstack.app.pdf.SignatureFont
import com.scanstack.app.pdf.StampArt
import com.scanstack.app.pdf.TextFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

val InkColors = listOf(Color(0xFF111827), Color(0xFF1D4ED8), Color(0xFFB91C1C))

fun SignatureFont.family(): FontFamily = FontFamily(Font(res))

fun TextFont.family(): FontFamily = when (this) {
    TextFont.Sans -> FontFamily.SansSerif
    TextFont.Serif -> FontFamily.Serif
    TextFont.Mono -> FontFamily.Monospace
    else -> FontFamily(Font(res!!))
}

@Composable
private fun InkPicker(selected: Int, onSelect: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        InkColors.forEachIndexed { i, c ->
            Box(
                Modifier
                    .padding(end = 10.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(c)
                    .border(if (selected == i) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable { onSelect(i) },
            )
        }
    }
}

// ---------------------------------------------------------------- signature creator

/** Create a signature by drawing it, typing it, or uploading a picture of it. */
@Composable
fun SignatureCreatorDialog(onDismiss: () -> Unit, onSave: (Bitmap) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var colorIndex by remember { mutableIntStateOf(0) }
    val color = InkColors[colorIndex]

    // Draw
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val strokeWidthPx = with(LocalDensity.current) { 3.2.dp.toPx() }

    // Type
    var name by remember { mutableStateOf("") }
    var font by remember { mutableStateOf(SignatureFont.GreatVibes) }

    // Upload
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uploaded by remember { mutableStateOf<Bitmap?>(null) }
    var cleaned by remember { mutableStateOf<Bitmap?>(null) }
    var removeBackground by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }

    fun load(uri: android.net.Uri?) {
        if (uri == null) return
        scope.launch {
            loading = true
            uploaded = withContext(Dispatchers.IO) { ImageTools.load(context, uri, 1400) }
            loading = false
        }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { load(it) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { load(it) }

    LaunchedEffect(uploaded, removeBackground) {
        val src = uploaded
        cleaned = if (src == null) null else withContext(Dispatchers.Default) {
            if (removeBackground) ImageTools.cropToContent(ImageTools.removeWhiteBackground(src)) else src
        }
    }

    val canSave = when (tab) {
        0 -> strokes.isNotEmpty()
        1 -> name.isNotBlank()
        else -> cleaned != null
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(Modifier.fillMaxWidth(0.94f), shape = RoundedCornerShape(24.dp)) {
            Column(
                Modifier
                    .heightIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 18.dp),
            ) {
                Text(
                    "Your signature",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(10.dp))
                PrimaryTabRow(selectedTabIndex = tab) {
                    listOf("Draw", "Type", "Upload").forEachIndexed { i, label ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                    }
                }
                Spacer(Modifier.height(14.dp))
                Column(Modifier.padding(horizontal = 20.dp)) {
                    when (tab) {
                        0 -> {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragStart = { current = listOf(it) },
                                            onDragEnd = {
                                                if (current.isNotEmpty()) strokes.add(current)
                                                current = emptyList()
                                            },
                                            onDragCancel = {
                                                if (current.isNotEmpty()) strokes.add(current)
                                                current = emptyList()
                                            },
                                        ) { change, _ ->
                                            change.consume()
                                            current = current + change.position
                                        }
                                    },
                            ) {
                                Canvas(Modifier.fillMaxSize()) {
                                    val baseY = size.height * 0.74f
                                    drawLine(Color(0xFFD1D5DB), Offset(24f, baseY), Offset(size.width - 24f, baseY), strokeWidth = 2f)
                                    (strokes + listOf(current)).forEach { points ->
                                        if (points.isNotEmpty()) {
                                            drawPath(
                                                path = smoothPath(points),
                                                color = color,
                                                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round),
                                            )
                                        }
                                    }
                                }
                                if (strokes.isEmpty() && current.isEmpty()) {
                                    Text(
                                        "Sign here with your finger",
                                        color = Color(0xFF9CA3AF),
                                        modifier = Modifier.align(Alignment.Center),
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                InkPicker(colorIndex) { colorIndex = it }
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { strokes.clear(); current = emptyList() }) { Text("Clear") }
                            }
                        }

                        1 -> {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Your name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("Choose a style", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            SignatureFont.entries.forEach { option ->
                                val selected = font == option
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.White)
                                        .border(
                                            if (selected) 2.dp else 1.dp,
                                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                            RoundedCornerShape(14.dp),
                                        )
                                        .clickable { font = option }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        name.ifBlank { "Your Name" },
                                        fontFamily = option.family(),
                                        fontSize = 34.sp,
                                        color = color,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        option.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF9CA3AF),
                                        modifier = Modifier.align(Alignment.BottomEnd),
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            InkPicker(colorIndex) { colorIndex = it }
                        }

                        else -> {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(170.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                val shown = cleaned
                                when {
                                    loading -> CircularProgressIndicator()
                                    shown != null -> Image(shown.asImageBitmap(), contentDescription = "Uploaded signature")
                                    else -> Text(
                                        "Take a clear photo of your signature on white paper, then choose it here.",
                                        color = Color(0xFF6B7280),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FilledTonalButton(
                                    onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Gallery")
                                }
                                FilledTonalButton(
                                    onClick = { pickFile.launch(arrayOf("image/*")) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Files")
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Remove white background", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "Keeps only the ink, so it sits cleanly on the page",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(checked = removeBackground, onCheckedChange = { removeBackground = it })
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                        Spacer(Modifier.width(10.dp))
                        Button(
                            enabled = canSave,
                            onClick = {
                                val bitmap = when (tab) {
                                    0 -> signatureBitmap(strokes.toList(), color.toArgb(), strokeWidthPx)
                                    1 -> StampArt.typedSignature(name.trim(), font, color.toArgb())
                                    else -> cleaned
                                }
                                if (bitmap != null) onSave(bitmap)
                            },
                        ) { Text("Use signature") }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- text

data class TextChoice(
    val text: String,
    val font: TextFont,
    val bold: Boolean,
    val italic: Boolean,
    val underline: Boolean,
)

/** Type text and choose its font and style. */
@Composable
fun TextStampDialog(initial: TextChoice?, color: Color, onDismiss: () -> Unit, onDone: (TextChoice) -> Unit) {
    var text by remember { mutableStateOf(initial?.text.orEmpty()) }
    var font by remember { mutableStateOf(initial?.font ?: TextFont.Sans) }
    var bold by remember { mutableStateOf(initial?.bold ?: false) }
    var italic by remember { mutableStateOf(initial?.italic ?: false) }
    var underline by remember { mutableStateOf(initial?.underline ?: false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(Modifier.fillMaxWidth(0.94f), shape = RoundedCornerShape(24.dp)) {
            Column(
                Modifier
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            ) {
                Text(
                    if (initial == null) "Add text" else "Edit text",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Text") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                Text("Font", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextFont.entries.take(3).forEach { option ->
                        FontChip(option, font == option, Modifier.weight(1f)) { font = option }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextFont.entries.drop(3).forEach { option ->
                        FontChip(option, font == option, Modifier.weight(1f)) { font = option }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Style", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StyleToggle(bold, { bold = it }, Icons.Filled.FormatBold, "Bold")
                    StyleToggle(italic, { italic = it }, Icons.Filled.FormatItalic, "Italic")
                    StyleToggle(underline, { underline = it }, Icons.Filled.FormatUnderlined, "Underline")
                }
                Spacer(Modifier.height(14.dp))
                Text("Preview", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    val preview = remember(text, font, bold, italic, underline, color) {
                        StampArt.textBitmap(text.ifBlank { "Your text" }, color.toArgb(), font, bold, italic, underline)
                    }
                    Image(preview.asImageBitmap(), contentDescription = "Preview", modifier = Modifier.height(36.dp))
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        enabled = text.isNotBlank(),
                        onClick = { onDone(TextChoice(text.trim(), font, bold, italic, underline)) },
                    ) { Text(if (initial == null) "Add" else "Done") }
                }
            }
        }
    }
}

@Composable
private fun FontChip(font: TextFont, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                font.label,
                fontFamily = font.family(),
                fontSize = if (font == TextFont.Handwriting || font == TextFont.Script) 17.sp else 14.sp,
                maxLines = 1,
            )
        },
        modifier = modifier,
    )
}

@Composable
private fun StyleToggle(checked: Boolean, onChange: (Boolean) -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    IconToggleButton(
        checked = checked,
        onCheckedChange = onChange,
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Icon(icon, contentDescription = label)
    }
}

// ---------------------------------------------------------------- drawing helpers

internal fun smoothPath(points: List<Offset>): Path {
    val path = Path()
    path.moveTo(points[0].x, points[0].y)
    if (points.size == 1) {
        path.lineTo(points[0].x + 0.5f, points[0].y + 0.5f)
        return path
    }
    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val cur = points[i]
        path.quadraticBezierTo(prev.x, prev.y, (prev.x + cur.x) / 2f, (prev.y + cur.y) / 2f)
    }
    path.lineTo(points.last().x, points.last().y)
    return path
}

/** Turns the drawn strokes into a tightly cropped, transparent, high-resolution image. */
internal fun signatureBitmap(strokes: List<List<Offset>>, color: Int, strokeWidth: Float): Bitmap {
    val all = strokes.flatten()
    val pad = strokeWidth * 2
    val minX = all.minOf { it.x } - pad
    val minY = all.minOf { it.y } - pad
    val maxX = all.maxOf { it.x } + pad
    val maxY = all.maxOf { it.y } + pad
    val scale = 2.5f
    val w = ((maxX - minX) * scale).roundToInt().coerceAtLeast(4)
    val h = ((maxY - minY) * scale).roundToInt().coerceAtLeast(4)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = android.graphics.Paint.Style.STROKE
        this.strokeWidth = strokeWidth * scale
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    for (points in strokes) {
        if (points.isEmpty()) continue
        val path = android.graphics.Path()
        fun px(o: Offset) = (o.x - minX) * scale
        fun py(o: Offset) = (o.y - minY) * scale
        path.moveTo(px(points[0]), py(points[0]))
        if (points.size == 1) {
            path.lineTo(px(points[0]) + 0.5f, py(points[0]) + 0.5f)
        } else {
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val cur = points[i]
                path.quadTo(px(prev), py(prev), (px(prev) + px(cur)) / 2f, (py(prev) + py(cur)) / 2f)
            }
            path.lineTo(px(points.last()), py(points.last()))
        }
        canvas.drawPath(path, paint)
    }
    return bitmap
}
