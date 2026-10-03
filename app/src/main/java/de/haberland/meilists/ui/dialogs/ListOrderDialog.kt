package de.haberland.meilists.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.haberland.meilists.model.ShoppingList

@Composable
fun ListOrderDialog(lists: List<ShoppingList>, onMove: (String, Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Listenreihenfolge") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                lists.forEachIndexed { index, list ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(list.name, Modifier.weight(1f))
                        IconButton(onClick = { onMove(list.id, -1) }, enabled = index > 0) {
                            Icon(Icons.Default.ArrowUpward, "${list.name} nach oben")
                        }
                        IconButton(onClick = { onMove(list.id, 1) }, enabled = index < lists.lastIndex) {
                            Icon(Icons.Default.ArrowDownward, "${list.name} nach unten")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fertig") } }
    )
}
