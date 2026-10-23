package com.scanstack.app.pdf

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTerminalField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import java.io.File
import java.io.IOException

enum class FieldType { Text, Check, Radio, Choice }

data class FormField(
    /** Full field name, used to find the field again when saving. */
    val name: String,
    val label: String,
    val type: FieldType,
    val value: String,
    /** Values to store (radio buttons and dropdowns). */
    val options: List<String> = emptyList(),
    /** What to show for each option. */
    val optionLabels: List<String> = emptyList(),
    val multiline: Boolean = false,
    val readOnly: Boolean = false,
)

data class FormInfo(val fields: List<FormField>, val isXfaOnly: Boolean)

object Forms {

    fun read(file: File): FormInfo {
        PDDocument.load(file).use { doc ->
            val form = doc.documentCatalog.acroForm ?: return FormInfo(emptyList(), false)
            val fields = mutableListOf<FormField>()
            for (field in form.fieldTree) {
                if (field !is PDTerminalField) continue
                val label = labelOf(field)
                when (field) {
                    is PDTextField -> fields += FormField(
                        name = field.fullyQualifiedName,
                        label = label,
                        type = FieldType.Text,
                        value = field.value.orEmpty(),
                        multiline = field.isMultiline,
                        readOnly = field.isReadOnly,
                    )
                    is PDCheckBox -> fields += FormField(
                        name = field.fullyQualifiedName,
                        label = label,
                        type = FieldType.Check,
                        value = if (field.isChecked) "true" else "false",
                        readOnly = field.isReadOnly,
                    )
                    is PDRadioButton -> {
                        val options = field.onValues.toList()
                        if (options.isNotEmpty()) {
                            fields += FormField(
                                name = field.fullyQualifiedName,
                                label = label,
                                type = FieldType.Radio,
                                value = field.value.orEmpty().takeIf { it in options }.orEmpty(),
                                options = options,
                                optionLabels = options.map { pretty(it) },
                                readOnly = field.isReadOnly,
                            )
                        }
                    }
                    is PDChoice -> {
                        val options = field.options.orEmpty()
                        if (options.isNotEmpty()) {
                            val shown = field.optionsDisplayValues.orEmpty()
                            fields += FormField(
                                name = field.fullyQualifiedName,
                                label = label,
                                type = FieldType.Choice,
                                value = field.value.orEmpty().firstOrNull().orEmpty(),
                                options = options,
                                optionLabels = if (shown.size == options.size) shown else options,
                                readOnly = field.isReadOnly,
                            )
                        }
                    }
                    else -> Unit // push buttons and digital-signature fields are skipped
                }
            }
            val xfaOnly = fields.isEmpty() && form.hasXFA()
            return FormInfo(fields, xfaOnly)
        }
    }

    /**
     * Writes [values] (field name -> value) into a copy of the PDF.
     * Returns the labels of fields that couldn't be filled.
     */
    fun fill(file: File, out: File, values: Map<String, String>, flatten: Boolean): List<String> {
        PDDocument.load(file).use { doc ->
            val form = doc.documentCatalog.acroForm ?: throw IOException("This PDF has no form fields")
            val problems = mutableListOf<String>()
            for ((name, value) in values) {
                val field = form.getField(name) ?: continue
                if (field.isReadOnly) continue
                try {
                    when (field) {
                        is PDCheckBox -> if (value == "true") field.check() else field.unCheck()
                        is PDRadioButton -> if (value.isNotEmpty()) field.setValue(value)
                        is PDChoice -> if (value.isNotEmpty()) field.setValue(value)
                        is PDTextField -> field.setValue(value)
                    }
                } catch (e: Exception) {
                    if (field is PDTextField) {
                        // The form's font can't draw these characters: store the value and let viewers draw it.
                        field.cosObject.setString(COSName.V, value)
                        form.needAppearances = true
                    } else {
                        problems += labelOf(field)
                    }
                }
            }
            if (flatten) {
                try {
                    form.flatten()
                } catch (e: Exception) {
                    problems += "Locking the fields"
                }
            }
            doc.save(out)
            return problems
        }
    }

    private fun labelOf(field: PDField): String {
        val alt = field.alternateFieldName
        if (!alt.isNullOrBlank() && alt.length <= 80) return alt.trim()
        return pretty(field.partialName ?: field.fullyQualifiedName ?: "Field")
    }

    /** "first_name" / "FirstName" / "form1[0].name[0]" -> "First name" */
    private fun pretty(raw: String): String {
        val cleaned = raw
            .replace(Regex("\\[\\d+]"), "")
            .substringAfterLast('.')
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.isEmpty()) return raw
        return cleaned.lowercase().replaceFirstChar { it.uppercase() }
    }
}
