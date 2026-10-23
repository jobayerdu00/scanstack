@file:OptIn(ExperimentalMaterial3Api::class)

package com.scanstack.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.scanstack.app.data.DocRepository
import com.scanstack.app.pdf.Enhance
import com.scanstack.app.pdf.EnhancePreset
import com.scanstack.app.pdf.EnhanceSettings
import com.scanstack.app.pdf.PdfTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

/** Brighten pages and boost their color, with a live preview. */
@Composable
fun EnhanceScreen(
    repo: DocRepository,
    file: File,
    snackbar: SnackbarHostState,
    message: (String) -> Unit,
    onBack: () -> Unit,
    onDone: (File) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(EnhancePreset.Bright.settings) }
    var preset by remember { mutableStateOf<EnhancePreset?>(EnhancePreset.Bright) }
    var page by remember { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(1) }
    var showOriginal by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(file) {
        pageCount = withContext(Dispatchers.IO) { PdfTools.pageCount(file) }.coerceAtLeast(1)
    }

    fun change(s: EnhanceSettings) {
        settings = s
        preset = EnhancePreset.entries.firstOrNull { it.settings == s }
    }

    fun apply() {
        scope.launch {
            busy = true
            try {
                val out = repo.newFile("${file.nameWithoutExtension} (enhanced)")
                withContext(Dispatchers.IO) { Enhance.apply(file, out, settings) }
                message("Enhanced ${if (pageCount == 1) "the page" else "all $pageCount pages"}")
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
        containerColor = AppColors.Background,
        topBar = {
            TopAppBar(
                colors = appBarColors(),
                title = { Text("Enhance") },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    Button(
                        onClick = { apply() },
                        enabled = !busy,
                        shape = RoundedCornerShape(Radius.m),
                        modifier = Modifier.padding(end = Space.s),
                    ) { Text("Apply") }
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
            // ----- Live preview -----
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .background(AppColors.Canvas)
                    .padding(Space.l),
                contentAlignment = Alignment.Center,
            ) {
                val density = LocalDensity.current
                val maxW = constraints.maxWidth
                val maxH = constraints.maxHeight
                val bmp by produceState<Bitmap?>(null, file, page, maxW) {
                    value = null
                    value = withContext(Dispatchers.IO) { PdfTools.renderPage(file, page, min(maxW, 1200)) }
                }
                val levels = remember(bmp) { bmp?.let { Enhance.levels(it) } }
                val b = bmp
                if (b == null) {
                    CircularProgressIndicator(color = AppColors.Teal)
                } else {
                    val ratio = b.width.toFloat() / b.height
                    val boxW = min(maxW.toFloat(), maxH * ratio)
                    val boxH = boxW / ratio
                    val filter = remember(settings, levels, showOriginal) {
                        if (showOriginal) null else ColorFilter.colorMatrix(ColorMatrix(Enhance.matrix(settings, levels)))
                    }
                    Image(
                        bitmap = b.asImageBitmap(),
                        contentDescription = "Preview",
                        colorFilter = filter,
                        modifier = Modifier
                            .size(with(density) { boxW.toDp() }, with(density) { boxH.toDp() })
                            .border(1.dp, AppColors.Border),
                    )
                }
                Text(
                    if (showOriginal) "Original" else "Preview",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.Text,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .clip(RoundedCornerShape(Radius.s))
                        .background(AppColors.Card)
                        .padding(horizontal = Space.s, vertical = Space.xs),
                )
            }

            Column(Modifier.padding(Space.l)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (pageCount > 1) {
                        IconButton(onClick = { page = (page - 1).coerceAtLeast(0) }, enabled = page > 0) {
                            Icon(AppIcons.ChevronLeft, contentDescription = "Previous page")
                        }
                        Text("Page ${page + 1} of $pageCount", style = MaterialTheme.typography.labelLarge, color = AppColors.Text)
                        IconButton(onClick = { page = (page + 1).coerceAtMost(pageCount - 1) }, enabled = page < pageCount - 1) {
                            Icon(AppIcons.ChevronRight, contentDescription = "Next page")
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("Compare", style = MaterialTheme.typography.labelLarge, color = AppColors.TextSecondary)
                    HSpace(8)
                    Switch(checked = showOriginal, onCheckedChange = { showOriginal = it })
                }

                Spacer(Modifier.height(Space.s))
                Text("Presets", style = MaterialTheme.typography.labelLarge, color = AppColors.TextSecondary)
                Spacer(Modifier.height(Space.s))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    EnhancePreset.entries.forEach { p ->
                        FilterChip(
                            selected = preset == p,
                            onClick = {
                                settings = p.settings
                                preset = p
                            },
                            label = { Text(p.label) },
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

                Spacer(Modifier.height(Space.l))
                WhiteCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = Space.l, vertical = Space.m)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Auto light balance", style = MaterialTheme.typography.titleSmall, color = AppColors.Text)
                                Text(
                                    "Makes the paper white and the text crisp",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.TextSecondary,
                                )
                            }
                            Switch(checked = settings.auto, onCheckedChange = { change(settings.copy(auto = it)) })
                        }
                        Spacer(Modifier.height(Space.s))
                        AdjustSlider("Brightness", settings.brightness) { change(settings.copy(brightness = it)) }
                        AdjustSlider("Contrast", settings.contrast) { change(settings.copy(contrast = it)) }
                        AdjustSlider("Color", settings.color) { change(settings.copy(color = it)) }
                    }
                }
                Spacer(Modifier.height(Space.s))
                Text(
                    "Applied to every page of a new copy. Your original stays unchanged. " +
                        "Text in the enhanced copy can't be selected, so use Get text on the original if you need it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextSecondary,
                )
                Spacer(Modifier.height(Space.xl))
            }
        }
    }

    if (busy) BusyDialog(if (pageCount > 1) "Enhancing $pageCount pages…" else "Enhancing…")
}

@Composable
private fun AdjustSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = AppColors.Text, modifier = Modifier.weight(1f))
            val shown = (value * 100).roundToInt()
            Text(
                if (shown > 0) "+$shown" else "$shown",
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.TextSecondary,
                modifier = Modifier.width(40.dp),
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = -1f..1f,
            colors = SliderDefaults.colors(
                thumbColor = AppColors.Primary,
                activeTrackColor = AppColors.Primary,
                inactiveTrackColor = AppColors.BlueSubtle,
            ),
        )
    }
}
