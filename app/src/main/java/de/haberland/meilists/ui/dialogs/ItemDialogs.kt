package de.haberland.meilists.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.haberland.meilists.model.ListItem

@Composable
fun EditItemDialog(
    item: ListItem,
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit
) {
    var text by remember(item.id) { mutableStateOf(item.text) }
    var area by remember(item.id) { mutableStateOf(item.area ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eintrag bearbeiten") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Bezeichnung") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = area,
                    onValueChange = { area = it },
                    label = { Text("Bereich (optional, z.B. Obst)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { if (text.isNotBlank()) onConfirm(text, area.ifBlank { null }) }) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
