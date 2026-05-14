@file:Suppress(
    "UNUSED_VALUE",
    "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE",
    "AssignedValueIsNeverRead"
)

package de.haberland.meilists.ui.dialogs

import android.content.ClipData
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import de.haberland.meilists.model.CatalogArea
import de.haberland.meilists.model.CatalogProduct
import de.haberland.meilists.model.Category
import de.haberland.meilists.ui.components.ColorPicker
import kotlinx.coroutines.launch

@Composable
fun SettingsDialog(
    category: Category,
    onDismiss: () -> Unit,
    onSave: (Boolean, Color, Boolean) -> Unit,
    onDelete: () -> Unit,
    allCategories: List<Category>,
    catalogAreas: List<CatalogArea> = emptyList(),
    catalogProducts: List<CatalogProduct> = emptyList(),
    catalogActions: CatalogManagerActions? = null
) {
    var hideChecked by remember { mutableStateOf(category.settings.hideCheckedItems) }
    var autoLearningEnabled by remember { mutableStateOf(category.settings.autoLearningEnabled) }
    var selectedColor by remember { mutableStateOf(Color(category.color)) }
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCatalogManager by remember { mutableStateOf(false) }

    if (showCatalogManager && catalogActions != null) {
        CatalogManagerDialog(
            category = category,
            areas = catalogAreas,
            products = catalogProducts,
            allCategories = allCategories,
            actions = catalogActions,
            onDismiss = { showCatalogManager = false }
        )
    }

    if (showDeleteConfirm) {
        DeleteCategoryConfirmDialog(
            category = category,
            onDismiss = { showDeleteConfirm = false },
            onDelete = onDelete
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Einstellungen: ${category.name}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = hideChecked, onCheckedChange = { hideChecked = it })
                    Text("Erledigte Einträge ausblenden")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = autoLearningEnabled, onCheckedChange = { autoLearningEnabled = it })
                    Text("Autolearning aktivieren")
                }
                Spacer(Modifier.height(16.dp))
                Text("Farbe wählen", style = MaterialTheme.typography.labelMedium)
                ColorPicker(selectedColor = selectedColor, onColorSelected = { selectedColor = it })

                if (catalogActions != null) {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { showCatalogManager = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Inventory, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Katalog verwalten (Bereiche/Produkte)")
                    }
                }

                Spacer(Modifier.height(16.dp))
                CategoryShareRow(
                    categoryId = category.id,
                    onCopyClick = {
                        coroutineScope.launch {
                            clipboard.setClipEntry(
                                ClipEntry(ClipData.newPlainText("Kategorie-ID", category.id))
                            )
                        }
                    }
                )

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Kategorie löschen")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(hideChecked, selectedColor, autoLearningEnabled) }) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun CategoryShareRow(
    categoryId: String,
    onCopyClick: () -> Unit
) {
    Text("Kategorie-ID (zum Teilen):", style = MaterialTheme.typography.labelMedium)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            categoryId,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onCopyClick) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = "ID kopieren",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun DeleteCategoryConfirmDialog(
    category: Category,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kategorie löschen?") },
        text = { Text("Möchtest du die Kategorie '${category.name}' und alle darin enthaltenen Listen wirklich löschen?") },
        confirmButton = {
            Button(
                onClick = onDelete,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Endgültig löschen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
