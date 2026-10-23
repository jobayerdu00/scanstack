@file:OptIn(ExperimentalMaterial3Api::class)

package com.scanstack.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.scanstack.app.data.DocRepository
import com.scanstack.app.pdf.PdfTools
import com.scanstack.app.pdf.SignatureStore
import com.scanstack.app.pdf.Stamp
import com.scanstack.app.pdf.StampArt
import com.scanstack.app.pdf.StampKind
import com.scanstack.app.pdf.StampWriter
import com.scanstack.app.pdf.TextFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun SignScreen(
    repo: DocRepository,
    file: File,
    snackbar: SnackbarHostState,
    message: (String) -> Unit,
    onBack: () -> Unit,
    onDone: (File) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pageCount by remember { mutableIntStateOf(0) }
    var page by remember(file) { mutableIntStateOf(0) }
    val stamps = remember(file) { mutableStateListOf<Stamp>() }
    var selected by remember { mutableStateOf<Long?>(null) }
    var signature by remember { mutableStateOf<Bitmap?>(null) }
    var showPad by remember { mutableStateOf(false) }
    var showSignatureChoice by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Stamp?>(null) } // text stamp being typed (new or existing)
    var busy by remember { mutableStateOf(false) }
    var ink by remember { mutableIntStateOf(0) }

    LaunchedEffect(file) {
        pageCount = withContext(Dispatchers.IO) { PdfTools.pageCount(file) }
        signature = withContext(Dispatchers.IO) { SignatureStore.load(context) }
    }

    fun nextId() = System.nanoTime()

    fun add(kind: StampKind, choice: TextChoice? = null) {
        val color = InkColors[ink].toArgb()
        val c = choice ?: TextChoice("", TextFont.Sans, false, false, false)
        val width = when (kind) {
            StampKind.Signature -> 0.32f
            StampKind.Check, StampKind.Cross -> 0.05f
            StampKind.Text -> StampArt.defaultTextWidth(StampArt.textBitmap(c.text, color, c.font, c.bold, c.italic, c.underline))
        }
        val stamp = Stamp(
            id = nextId(),
            page = page,
            kind = kind,
            x = (0.5f - width / 2f).coerceIn(0f, 1f),
            y = 0.42f,
            width = width.coerceIn(0.02f, 1f),
            text = c.text,
            color = color,
            font = c.font,
            bold = c.bold,
            italic = c.italic,
            underline = c.underline,
        )
        stamps.add(stamp)
        selected = stamp.id
    }

    fun update(id: Long, change: (Stamp) -> Stamp) {
        val i = stamps.indexOfFirst { it.id == id }
        if (i >= 0) stamps[i] = change(stamps[i])
    }

    fun save() {
        scope.launch {
            busy = true
            try {
                val out = repo.newFile("${file.nameWithoutExtension} (signed)")
                withContext(Dispatchers.IO) { StampWriter.apply(file, out, stamps.toList(), signature) }
                message("Saved as a new copy")
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
                title = { Text("Fill & Sign") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(AppIcons.Back, contentDescription = "Back") }
                },
                actions = {
                    Button(
                        onClick = { save() },
                        enabled = stamps.isNotEmpty() && !busy,
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text("Save") }
                },
            )
        },
        bottomBar = {
            Surface(color = AppColors.Card, modifier = Modifier.topHairline()) {
                Column(Modifier.navigationBarsPadding().padding(vertical = 8.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Ink", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HSpace(10)
                        InkColors.forEachIndexed { i, c ->
                            Box(
                                Modifier
                                    .padding(end = 8.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(
                                        width = if (ink == i) 3.dp else 0.dp,
                                        color = AppColors.Teal,
                                        shape = CircleShape,
                                    )
                                    .clickable {
                                        ink = i
                                        val id = selected
                                        if (id != null) update(id) { s ->
                                            if (s.kind == StampKind.Signature) s else s.copy(color = c.toArgb())
                                        }
                                    },
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "Tap a tool, then drag it into place",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        SignTool("Signature", AppIcons.Signature, AppColors.TealDark, AppColors.TealSubtle) {
                            if (signature == null) showPad = true else showSignatureChoice = true
                        }
                        SignTool("Text", AppIcons.TextTool, AppColors.Primary) {
                            editing = Stamp(id = 0, page = page, kind = StampKind.Text, x = 0f, y = 0f, width = 0f)
                        }
                        SignTool("Date", AppIcons.Calendar, AppColors.Primary) {
                            add(
                                StampKind.Text,
                                TextChoice(SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date()), TextFont.Sans, false, false, false),
                            )
                        }
                        SignTool("Check", AppIcons.Check, AppColors.Primary) { add(StampKind.Check) }
                        SignTool("Cross", AppIcons.Close, AppColors.Primary) { add(StampKind.Cross) }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(AppColors.Canvas),
        ) {
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                val density = LocalDensity.current
                val maxW = constraints.maxWidth
                val maxH = constraints.maxHeight
                val pageBitmap by produceState<Bitmap?>(null, file, page, maxW) {
                    value = null
                    value = withContext(Dispatchers.IO) { PdfTools.renderPage(file, page, min(maxW, 1600)) }
                }
                val bmp = pageBitmap
                if (bmp == null || maxW <= 0 || maxH <= 0) {
                    CircularProgressIndicator()
                } else {
                    val ratio = bmp.width.toFloat() / bmp.height
                    val boxW = min(maxW.toFloat(), maxH * ratio)
                    val boxH = boxW / ratio
                    Box(
                        Modifier
                            .size(with(density) { boxW.toDp() }, with(density) { boxH.toDp() })
                            .shadow(6.dp, RoundedCornerShape(2.dp))
                            .background(Color.White)
                            .pointerInput(Unit) { detectTapGestures { selected = null } },
                    ) {
                        Image(bmp.asImageBitmap(), contentDescription = "Page ${page + 1}", modifier = Modifier.fillMaxSize())
                        stamps.filter { it.page == page }.forEach { stamp ->
                            val art = StampArt.bitmap(stamp, signature)
                            if (art != null) key(stamp.id) {
                                StampView(
                                    stamp = stamp,
                                    art = art,
                                    boxW = boxW,
                                    boxH = boxH,
                                    isSelected = selected == stamp.id,
                                    onSelect = { selected = stamp.id },
                                    onMove = { dx, dy ->
                                        update(stamp.id) { s ->
                                            val hFrac = s.width * boxW * art.height / art.width / boxH
                                            s.copy(
                                                x = (s.x + dx).coerceIn(-s.width * 0.5f, 1f - s.width * 0.5f),
                                                y = (s.y + dy).coerceIn(-hFrac * 0.5f, 1f - hFrac * 0.5f),
                                            )
                                        }
                                    },
                                    onResize = { dw -> update(stamp.id) { s -> s.copy(width = (s.width + dw).coerceIn(0.02f, 1f)) } },
                                    onDelete = {
                                        stamps.removeAll { it.id == stamp.id }
                                        selected = null
                                    },
                                    onEdit = if (stamp.kind == StampKind.Text) ({ editing = stamp }) else null,
                                )
                            }
                        }
                    }
                }
            }
            if (pageCount > 1) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { page = max(0, page - 1); selected = null }, enabled = page > 0) {
                        Icon(AppIcons.ChevronLeft, contentDescription = "Previous page")
                    }
                    Text("Page ${page + 1} of $pageCount", style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = { page = min(pageCount - 1, page + 1); selected = null }, enabled = page < pageCount - 1) {
                        Icon(AppIcons.ChevronRight, contentDescription = "Next page")
                    }
                }
            }
        }
    }

    if (showSignatureChoice) {
        val sig = signature
        AlertDialog(
            onDismissRequest = { showSignatureChoice = false },
            title = { Text("Your signature") },
            text = {
                if (sig != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(sig.asImageBitmap(), contentDescription = "Saved signature")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showSignatureChoice = false
                    add(StampKind.Signature)
                }) { Text("Use it") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSignatureChoice = false
                    showPad = true
                }) { Text("New signature") }
            },
        )
    }

    if (showPad) {
        SignatureCreatorDialog(
            onDismiss = { showPad = false },
            onSave = { bitmap ->
                showPad = false
                signature = bitmap
                scope.launch(Dispatchers.IO) { SignatureStore.save(context, bitmap) }
                add(StampKind.Signature)
            },
        )
    }

    editing?.let { target ->
        val isNew = target.id == 0L
        TextStampDialog(
            initial = if (isNew) null else TextChoice(target.text, target.font, target.bold, target.italic, target.underline),
            color = InkColors[ink],
            onDismiss = { editing = null },
            onDone = { choice ->
                editing = null
                if (isNew) {
                    add(StampKind.Text, choice)
                } else {
                    update(target.id) { s ->
                        val old = StampArt.textBitmap(s.text, s.color, s.font, s.bold, s.italic, s.underline)
                        val new = StampArt.textBitmap(choice.text, s.color, choice.font, choice.bold, choice.italic, choice.underline)
                        s.copy(
                            text = choice.text,
                            font = choice.font,
                            bold = choice.bold,
                            italic = choice.italic,
                            underline = choice.underline,
                            width = (s.width * new.width / old.width).coerceIn(0.02f, 1f),
                        )
                    }
                }
            },
        )
    }

    if (busy) BusyDialog("Saving…")
}

@Composable
private fun StampView(
    stamp: Stamp,
    art: Bitmap,
    boxW: Float,
    boxH: Float,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onMove: (Float, Float) -> Unit,
    onResize: (Float) -> Unit,
    onDelete: () -> Unit,
    onEdit: (() -> Unit)?,
) {
    val density = LocalDensity.current
    val move by rememberUpdatedState(onMove)
    val resize by rememberUpdatedState(onResize)
    val select by rememberUpdatedState(onSelect)
    val wPx = stamp.width * boxW
    val hPx = wPx * art.height / art.width
    val primary = MaterialTheme.colorScheme.primary

    Box(
        Modifier
            .offset { IntOffset((stamp.x * boxW).roundToInt(), (stamp.y * boxH).roundToInt()) }
            .size(with(density) { wPx.toDp() }, with(density) { hPx.toDp() })
            .pointerInput(stamp.id) { detectTapGestures { select() } }
            .pointerInput(stamp.id, boxW, boxH) {
                detectDragGestures(onDragStart = { select() }) { change, amount ->
                    change.consume()
                    move(amount.x / boxW, amount.y / boxH)
                }
            }
            .then(
                if (isSelected) Modifier.border(1.5.dp, primary, RoundedCornerShape(3.dp)) else Modifier,
            ),
    ) {
        Image(art.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
        if (isSelected) {
            Handle(AppIcons.Close, Color(0xFFDC2626), Modifier.align(Alignment.TopEnd).offset(12.dp, (-12).dp)) {
                Modifier.clickable { onDelete() }
            }
            if (onEdit != null) {
                Handle(AppIcons.Edit, primary, Modifier.align(Alignment.TopStart).offset((-12).dp, (-12).dp)) {
                    Modifier.clickable { onEdit() }
                }
            }
            Handle(AppIcons.Resize, primary, Modifier.align(Alignment.BottomEnd).offset(12.dp, 12.dp)) {
                Modifier.pointerInput(stamp.id, boxW) {
                    detectDragGestures { change, amount ->
                        change.consume()
                        resize((amount.x + amount.y) / 2f / boxW)
                    }
                }
            }
        }
    }
}

@Composable
private fun Handle(icon: ImageVector, color: Color, modifier: Modifier, action: () -> Modifier) {
    Box(
        modifier
            .size(26.dp)
            .shadow(3.dp, CircleShape)
            .clip(CircleShape)
            .background(color)
            .then(action()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun SignTool(label: String, icon: ImageVector, tint: Color, tile: Color = AppColors.BlueSubtle, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(Radius.m))
            .clickable(onClick = onClick)
            .padding(horizontal = Space.s, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ToolIcon(icon, tint, 44.dp, tile)
        Spacer(Modifier.height(Space.xs))
        Text(label, style = MaterialTheme.typography.labelMedium, color = AppColors.Text)
    }
}
