package de.haberland.meilists.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.haberland.meilists.domain.moveItemTargets
import de.haberland.meilists.model.Category
import de.haberland.meilists.model.ListItem
import de.haberland.meilists.model.ShoppingList

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

@Composable
fun MoveItemDialog(
    listItem: ListItem,
    categories: List<Category>,
    lists: List<ShoppingList>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val targets = remember(categories, lists, listItem.listId) {
        moveItemTargets(categories, lists, listItem.listId)
    }
    var selectedListId by remember(listItem.id) { mutableStateOf<String?>(null) }
    val hasMoveTargets = targets.any { !it.isCurrentList }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eintrag verschieben") },
        text = {
            if (hasMoveTargets) {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    targets.groupBy { it.categoryId }.values.forEach { categoryTargets ->
                        item(key = "category-${categoryTargets.first().categoryId}") {
                            Text(
                                text = categoryTargets.first().categoryName,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        items(categoryTargets, key = { target -> target.listId }) { target ->
                            MoveTargetRow(
                                listName = target.listName,
                                isCurrentList = target.isCurrentList,
                                selected = selectedListId == target.listId,
                                onClick = { selectedListId = target.listId }
                            )
                        }
                    }
                }
            } else {
                Text("Es gibt noch keine andere Liste, in die dieser Eintrag verschoben werden kann.")
            }
        },
        confirmButton = {
            Button(
                enabled = selectedListId != null,
                onClick = {
                    selectedListId?.let(onConfirm)
                }
            ) {
                Text("Verschieben")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun MoveTargetRow(
    listName: String,
    isCurrentList: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isCurrentList, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            enabled = !isCurrentList
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(listName)
            if (isCurrentList) {
                Text("Aktuelle Liste")
            }
        }
    }
}
