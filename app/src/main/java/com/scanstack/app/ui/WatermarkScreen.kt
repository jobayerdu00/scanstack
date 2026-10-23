@file:OptIn(ExperimentalMaterial3Api::class)

package com.scanstack.app.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.scanstack.app.data.DocRepository
import com.scanstack.app.pdf.ImageTools
import com.scanstack.app.pdf.PdfTools
import com.scanstack.app.pdf.Watermark
import com.scanstack.app.pdf.WatermarkLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

private val MarkColors = listOf(
    Color(0xFF6B7280), // grey
    Color(0xFFDC2626), // red
    Color(0xFF2563EB), // blue
    Color(0xFF111827), // black
    Color(0xFF0E8A85), // teal
)

@Composable
fun WatermarkScreen(
    repo: DocRepository,
    file: File,
    snackbar: SnackbarHostState,
    message: (String) -> Unit,
    onBack: () -> Unit,
    onDone: (File) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var useImage by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("CONFIDENTIAL") }
    var colorIndex by remember { mutableIntStateOf(0) }
    var rawImage by remember { mutableStateOf<Bitmap?>(null) }
    var image by remember { mutableStateOf<Bitmap?>(null) }
    var removeBackground by remember { mutableStateOf(true) }
    var layout by remember { mutableStateOf(WatermarkLayout.Diagonal) }
    var scale by remember { mutableFloatStateOf(0.7f) }
    var opacity by remember { mutableFloatStateOf(0.25f) }
    var busy by remember { mutableStateOf(false) }
    var loadingImage by remember { mutableStateOf(false) }

    fun loadImage(uri: android.net.Uri?) {
        if (uri == null) return
        scope.launch {
            loadingImage = true
            rawImage = withContext(Dispatchers.IO) { ImageTools.load(context, uri, 1600) }
            loadingImage = false
            if (rawImage == null) message("Couldn't open that picture")
        }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { loadImage(it) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { loadImage(it) }

    LaunchedEffect(rawImage, removeBackground) {
        val src = rawImage
        image = if (src == null) null else withContext(Dispatchers.Default) {
            if (removeBackground) ImageTools.cropToContent(ImageTools.removeWhiteBackground(src)) else src
        }
    }

    val art: Bitmap? = remember(useImage, text, colorIndex, image) {
        if (useImage) image else text.trim().takeIf { it.isNotEmpty() }?.let { Watermark.textBitmap(it, MarkColors[colorIndex].toArgb()) }
    }

    fun switchMode(toImage: Boolean) {
        if (toImage == useImage) return
        useImage = toImage
        if (toImage) {
            layout = WatermarkLayout.Center
            scale = 0.45f
            opacity = 0.3f
        } else {
            layout = WatermarkLayout.Diagonal
            scale = 0.7f
            opacity = 0.25f
        }
    }

    fun apply() {
        val a = art ?: return
        scope.launch {
            busy = true
            try {
                val out = repo.newFile("${file.nameWithoutExtension} (watermarked)")
                withContext(Dispatchers.IO) { Watermark.apply(file, out, a, layout, scale, opacity) }
                message("Watermark added to every page")
                onDone(out)
            } catch (e: Exception) {
                message(friendlyError(e))
            } catch (e: OutOfMemoryError) {
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
                title = { Text("Watermark") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(AppIcons.Back, contentDescription = "Back") }
                },
                actions = {
                    Button(onClick = { apply() }, enabled = art != null && !busy, modifier = Modifier.padding(end = 8.dp)) {
                        Text("Apply")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // ----- Live preview of page 1 -----
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .background(AppColors.Canvas)
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                val density = LocalDensity.current
                val maxW = constraints.maxWidth
                val maxH = constraints.maxHeight
                val page by produceState<Bitmap?>(null, file, maxW) {
                    value = withContext(Dispatchers.IO) { PdfTools.renderPage(file, 0, min(maxW, 1200)) }
                }
                val bmp = page
                if (bmp == null) {
                    CircularProgressIndicator()
                } else {
                    val ratio = bmp.width.toFloat() / bmp.height
                    val boxW = min(maxW.toFloat(), maxH * ratio)
                    val boxH = boxW / ratio
                    Box(
                        Modifier
                            .size(with(density) { boxW.toDp() }, with(density) { boxH.toDp() })
                            .shadow(6.dp, RoundedCornerShape(2.dp))
                            .background(Color.White),
                    ) {
                        Image(bmp.asImageBitmap(), contentDescription = "Page 1", modifier = Modifier.fillMaxSize())
                        val a = art
                        if (a != null) {
                            val mark = remember(a) { a.asImageBitmap() }
                            Canvas(Modifier.fillMaxSize()) {
                                val aspect = a.width.toFloat() / a.height
                                Watermark.placements(layout, size.width, size.height, aspect, scale).forEach { p ->
                                    rotate(degrees = -p.angle, pivot = Offset(p.cx, p.cy)) {
                                        drawImage(
                                            image = mark,
                                            srcOffset = IntOffset.Zero,
                                            srcSize = IntSize(a.width, a.height),
                                            dstOffset = IntOffset((p.cx - p.w / 2f).roundToInt(), (p.cy - p.h / 2f).roundToInt()),
                                            dstSize = IntSize(p.w.roundToInt().coerceAtLeast(1), p.h.roundToInt().coerceAtLeast(1)),
                                            alpha = opacity,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Column(Modifier.padding(16.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !useImage,
                        onClick = { switchMode(false) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text("Text") }
                    SegmentedButton(
                        selected = useImage,
                        onClick = { switchMode(true) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text("Image / logo") }
                }
                Spacer(Modifier.height(16.dp))

                if (!useImage) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Watermark text") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("CONFIDENTIAL", "DRAFT", "COPY", "PAID", "SAMPLE").forEach { quick ->
                            Text(
                                quick,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AppColors.BlueSubtle)
                                    .clickable { text = quick }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Color", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(64.dp))
                        MarkColors.forEachIndexed { i, c ->
                            Box(
                                Modifier
                                    .padding(end = 10.dp)
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(if (colorIndex == i) 3.dp else 0.dp, AppColors.Teal, CircleShape)
                                    .clickable { colorIndex = i },
                            )
                        }
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AppColors.Card),
                        border = BorderStroke(1.dp, AppColors.Border),
                        shape = RoundedCornerShape(Radius.l),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                        .padding(6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val img = image
                                    when {
                                        loadingImage -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                        img != null -> Image(img.asImageBitmap(), contentDescription = "Chosen image")
                                        else -> Text("No image", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9CA3AF))
                                    }
                                }
                                HSpace(12)
                                Text(
                                    "Choose a logo, stamp or any picture from your phone.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FilledTonalButton(
                                    onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(AppIcons.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                    HSpace(6)
                                    Text("Gallery")
                                }
                                FilledTonalButton(
                                    onClick = { pickFile.launch(arrayOf("image/*")) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(AppIcons.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                                    HSpace(6)
                                    Text("Files")
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Remove white background", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Switch(checked = removeBackground, onCheckedChange = { removeBackground = it })
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Text("Position", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WatermarkLayout.entries.forEach { option ->
                        FilterChip(
                            selected = layout == option,
                            onClick = { layout = option },
                            label = { Text(option.label) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                LabeledSlider("Size", scale, 0.1f..1f) { scale = it }
                LabeledSlider("Opacity", opacity, 0.05f..1f) { opacity = it }
                Spacer(Modifier.height(8.dp))
                Text(
                    "The watermark is added to every page of a new copy. Your original stays unchanged.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (busy) BusyDialog("Adding watermark…")
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(64.dp))
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
        Text(
            "${(value * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp).padding(start = 8.dp),
        )
    }
}
