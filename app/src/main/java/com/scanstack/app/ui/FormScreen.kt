@file:OptIn(ExperimentalMaterial3Api::class)

package com.scanstack.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scanstack.app.data.DocRepository
import com.scanstack.app.pdf.FieldType
import com.scanstack.app.pdf.FormField
import com.scanstack.app.pdf.FormInfo
import com.scanstack.app.pdf.Forms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun FormScreen(
    repo: DocRepository,
    file: File,
    snackbar: SnackbarHostState,
    message: (String) -> Unit,
    onBack: () -> Unit,
    onDone: (File) -> Unit,
    onUseFillAndSign: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var info by remember(file) { mutableStateOf<FormInfo?>(null) }
    val values = remember(file) { mutableStateMapOf<String, String>() }
    var flatten by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(file) {
        val result = withContext(Dispatchers.IO) {
            runCatching { Forms.read(file) }.getOrElse { FormInfo(emptyList(), false) }
        }
        result.fields.forEach { values[it.name] = it.value }
        info = result
    }

    fun save() {
        val fields = info?.fields ?: return
        scope.launch {
            busy = true
            try {
                val changed = fields.associate { it.name to (values[it.name] ?: it.value) }
                val out = repo.newFile("${file.nameWithoutExtension} (filled)")
                val problems = withContext(Dispatchers.IO) { Forms.fill(file, out, changed, flatten) }
                message(
                    if (problems.isEmpty()) "Saved the filled form as a new copy"
                    else "Saved. Couldn't fill: ${problems.take(3).joinToString()}",
                )
                onDone(out)
            } catch (e: Exception) {
                message(friendlyError(e))
            } finally {
                busy = false
            }
        }
    }

    val fields = info?.fields.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                colors = appBarColors(),
                title = { Text("Fill form") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(AppIcons.Back, contentDescription = "Back") }
                },
                actions = {
                    if (fields.isNotEmpty()) {
                        Button(onClick = { save() }, enabled = !busy, modifier = Modifier.padding(end = 8.dp)) { Text("Save") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val current = info
        when {
            current == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            current.fields.isEmpty() -> NoFields(Modifier.padding(padding), current.isXfaOnly, onUseFillAndSign)
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 4.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        "${current.fields.size} fields found. Fill them in and tap Save. Your original stays unchanged.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(current.fields, key = { it.name }) { field ->
                    FieldCard(field, values[field.name] ?: field.value) { values[field.name] = it }
                }
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AppColors.TealSubtle),
                        shape = RoundedCornerShape(Radius.l),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier
                                .toggleable(value = flatten, onValueChange = { flatten = it }, role = Role.Switch)
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Lock the answers", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Turns fields into normal text so nobody can change them. Good before sending.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                            HSpace(12)
                            Switch(checked = flatten, onCheckedChange = null)
                        }
                    }
                }
            }
        }
    }

    if (busy) BusyDialog("Saving form…")
}

@Composable
private fun FieldCard(field: FormField, value: String, onChange: (String) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.Card),
        border = BorderStroke(1.dp, AppColors.Border),
        shape = RoundedCornerShape(Radius.l),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            when (field.type) {
                FieldType.Text -> OutlinedTextField(
                    value = value,
                    onValueChange = onChange,
                    label = { Text(field.label) },
                    singleLine = !field.multiline,
                    minLines = if (field.multiline) 3 else 1,
                    enabled = !field.readOnly,
                    modifier = Modifier.fillMaxWidth(),
                )

                FieldType.Check -> Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = value == "true",
                            enabled = !field.readOnly,
                            onValueChange = { onChange(if (it) "true" else "false") },
                            role = Role.Checkbox,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = value == "true", onCheckedChange = null, enabled = !field.readOnly)
                    HSpace(8)
                    Text(field.label, style = MaterialTheme.typography.bodyLarge)
                }

                FieldType.Radio -> {
                    Text(field.label, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    field.options.forEachIndexed { i, option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = value == option,
                                    enabled = !field.readOnly,
                                    onClick = { onChange(option) },
                                    role = Role.RadioButton,
                                )
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = value == option, onClick = null, enabled = !field.readOnly)
                            HSpace(8)
                            Text(field.optionLabels.getOrElse(i) { option }, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }

                FieldType.Choice -> {
                    var open by remember { mutableStateOf(false) }
                    val shown = field.options.indexOf(value).let { if (it >= 0) field.optionLabels.getOrElse(it) { value } else "" }
                    Text(field.label, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Box {
                        OutlinedButton(
                            onClick = { open = true },
                            enabled = !field.readOnly,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(shown.ifEmpty { "Choose…" }, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                            Icon(AppIcons.ChevronDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            field.options.forEachIndexed { i, option ->
                                DropdownMenuItem(
                                    text = { Text(field.optionLabels.getOrElse(i) { option }) },
                                    onClick = {
                                        open = false
                                        onChange(option)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoFields(modifier: Modifier, xfa: Boolean, onUseFillAndSign: () -> Unit) {
    Column(
        modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ToolIcon(Tool.Form, 64.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            if (xfa) "This form uses a format phones can't edit" else "No fillable fields in this PDF",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "You can still type anywhere on the page, add dates, ticks and your signature with Fill & Sign.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Button(onClick = onUseFillAndSign) {
            Icon(AppIcons.Form, contentDescription = null, modifier = Modifier.size(18.dp))
            HSpace(8)
            Text("Open Fill & Sign")
        }
    }
}
