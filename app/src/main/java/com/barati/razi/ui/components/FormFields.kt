package com.barati.razi.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.barati.razi.R
import com.barati.razi.core.form.Answer
import com.barati.razi.core.form.FieldType
import com.barati.razi.core.form.FormField
import com.barati.razi.core.util.Jalali
import com.barati.razi.core.util.JalaliDate
import com.barati.razi.core.util.toPersianDigits
import java.time.LocalDate

@Composable
fun FieldLabel(label: String, required: Boolean) {
    Text(
        if (required) "$label *" else label,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
fun DynamicField(field: FormField, answer: Answer?, error: String?, onChange: (Answer?) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (field.type) {
            FieldType.TEXT, FieldType.TEXTAREA -> OutlinedTextField(
                value = (answer as? Answer.Text)?.value.orEmpty(),
                onValueChange = { onChange(Answer.Text(it)) },
                label = { Text(if (field.required) "${field.label} *" else field.label) },
                isError = error != null,
                singleLine = field.type == FieldType.TEXT,
                minLines = if (field.type == FieldType.TEXTAREA) 3 else 1,
                modifier = Modifier.fillMaxWidth(),
            )
            FieldType.DATE -> {
                FieldLabel(field.label, field.required)
                val date = (answer as? Answer.Text)?.value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                JalaliDateField(date, onChange = { onChange(it?.let { d -> Answer.Text(d.toString()) }) })
            }
            FieldType.SINGLE -> {
                FieldLabel(field.label, field.required)
                val selected = (answer as? Answer.Text)?.value
                field.options.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp).selectable(
                            selected = option == selected,
                            role = Role.RadioButton,
                            onClick = { onChange(if (option == selected && !field.required) null else Answer.Text(option)) },
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(option, Modifier.padding(start = 8.dp))
                    }
                }
            }
            FieldType.MULTI -> {
                FieldLabel(field.label, field.required)
                val selected = (answer as? Answer.Choices)?.values.orEmpty()
                field.options.forEach { option ->
                    val checked = option in selected
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp).toggleable(
                            value = checked,
                            role = Role.Checkbox,
                            onValueChange = { now ->
                                val next = if (now) selected + option else selected - option
                                onChange(Answer.Choices(field.options.filter { it in next }))
                            },
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Text(option, Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        ErrorText(error)
    }
}

/** انتخاب تاریخ شمسی با سه فهرست سال/ماه/روز؛ خروجی میلادی (LocalDate). */
@Composable
fun JalaliDateField(value: LocalDate?, onChange: (LocalDate?) -> Unit, maxDate: LocalDate = LocalDate.now()) {
    val today = LocalDate.now()
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Text(value?.let { Jalali.formatLong(it) } ?: "انتخاب تاریخ")
    }
    if (open) {
        val initial = Jalali.fromGregorian(value ?: minOf(today, maxDate))
        var year by remember { mutableStateOf(initial.year) }
        var month by remember { mutableStateOf(initial.month) }
        var day by remember { mutableStateOf(initial.day) }
        val maxJ = Jalali.fromGregorian(maxDate)
        val days = Jalali.monthLength(year, month)
        val safeDay = day.coerceAtMost(days)
        val candidate = Jalali.toGregorian(JalaliDate(year, month, safeDay))
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("انتخاب تاریخ") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Picker("روز", safeDay, (1..days).toList(), { toPersianDigits(it.toString()) }) { day = it }
                    Picker("ماه", month, (1..12).toList(), { Jalali.MONTH_NAMES[it - 1] }) { month = it }
                    Picker("سال", year, ((maxJ.year - 5)..maxJ.year).toList().reversed(), { toPersianDigits(it.toString()) }) {
                        year = it
                    }
                    if (candidate.isAfter(maxDate)) {
                        ErrorText(if (maxDate == today) "تاریخ نمی‌تواند بعد از امروز باشد" else "تاریخ خارج از محدوده مجاز است")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !candidate.isAfter(maxDate),
                    onClick = {
                        onChange(candidate)
                        open = false
                    },
                ) { Text("تأیید") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("انصراف") } },
        )
    }
}

@Composable
private fun <T> Picker(label: String, value: T, options: List<T>, display: (T) -> String, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(0.3f))
        Box(Modifier.weight(0.7f)) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(display(value)) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(display(option)) }, onClick = {
                        onSelect(option)
                        expanded = false
                    })
                }
            }
        }
    }
}

/** فهرست کشویی ساده برای انتخاب یک مورد (مثلاً واحد). */
@Composable
fun <T> SelectField(
    label: String,
    selected: T?,
    options: List<T>,
    display: (T) -> String,
    onSelect: (T?) -> Unit,
    allowNone: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selected?.let(display) ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
        )
        // لایه شفاف روی فیلد برای باز کردن فهرست
        Box(Modifier.matchParentSize().clickable { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (allowNone) {
                DropdownMenuItem(text = { Text(stringResource(R.string.none_option)) }, onClick = {
                    onSelect(null)
                    expanded = false
                })
            }
            options.forEach { option ->
                DropdownMenuItem(text = { Text(display(option)) }, onClick = {
                    onSelect(option)
                    expanded = false
                })
            }
        }
    }
}
