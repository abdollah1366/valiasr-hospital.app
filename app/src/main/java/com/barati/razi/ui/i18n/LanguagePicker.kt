package com.barati.razi.ui.i18n

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import com.barati.razi.R
import com.barati.razi.core.i18n.AppLanguage

/** دکمه زبان در نوار بالای صفحه‌های مراجعان؛ با لمس، فهرست زبان‌ها باز می‌شود. */
@Composable
fun LanguageAction(current: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) {
        Icon(Icons.Outlined.Language, contentDescription = stringResource(R.string.language), modifier = Modifier.size(20.dp))
        Text(current.code.uppercase(), Modifier.padding(start = 4.dp))
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.language)) },
            text = {
                Column {
                    AppLanguage.entries.forEach { lang ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelect(lang); open = false }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = lang == current, onClick = { onSelect(lang); open = false })
                            Text(lang.nativeName)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}
