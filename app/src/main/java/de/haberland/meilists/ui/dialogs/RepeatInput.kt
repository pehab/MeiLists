package de.haberland.meilists.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import de.haberland.meilists.domain.validRepeatDays

@Composable
fun RepeatInput(enabled: Boolean, days: String, onEnabled: (Boolean) -> Unit, onDays: (String) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = enabled, onCheckedChange = onEnabled)
            Text("Wiederholen")
        }
        if (enabled) {
            OutlinedTextField(
                value = days,
                onValueChange = onDays,
                label = { Text("Alle X Tage") },
                supportingText = { Text("1–3650 Tage ab dem Abhaken") },
                isError = validRepeatDays(days.toIntOrNull()) == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
