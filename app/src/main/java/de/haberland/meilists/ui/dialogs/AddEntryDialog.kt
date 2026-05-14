package de.haberland.meilists.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import de.haberland.meilists.model.CatalogArea
import de.haberland.meilists.model.CatalogProduct
import de.haberland.meilists.model.Category
import de.haberland.meilists.ui.components.ColorPicker

enum class AddType { CATEGORY, LIST, ITEM }

@Composable
fun AddEntryDialog(
    type: AddType,
    onDismiss: () -> Unit,
    onConfirm: (String, Color?, String?, String?, Boolean, Boolean) -> Unit,
    catalogProducts: List<CatalogProduct> = emptyList(),
    catalogAreas: List<CatalogArea> = emptyList(),
    allCategories: List<Category> = emptyList()
) {
    var text by remember { mutableStateOf("") }
    var area by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(Color(0xFF6200EE)) }

    var importSource by remember { mutableStateOf<Category?>(null) }
    var importAreas by remember { mutableStateOf(true) }
    var importProducts by remember { mutableStateOf(true) }
    var sourceExpanded by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    var areaExpanded by remember { mutableStateOf(false) }

    val productSuggestions = if (type == AddType.ITEM && text.isNotBlank()) {
        catalogProducts
            .filter { it.name.contains(text, ignoreCase = true) && it.name != text }
            .take(5)
    } else {
        emptyList()
    }

    val areaSuggestions = if (type == AddType.ITEM && area.isNotBlank()) {
        catalogAreas
            .filter { it.name.contains(area, ignoreCase = true) && it.name != area }
            .take(3)
    } else {
        emptyList()
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (type) {
                    AddType.CATEGORY -> "Kategorie hinzufügen"
                    AddType.LIST -> "Liste hinzufügen"
                    AddType.ITEM -> "Eintrag hinzufügen"
                }
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )

                if (productSuggestions.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column {
                            productSuggestions.forEach { product ->
                                Text(
                                    text = product.name + (product.defaultArea?.let { " ($it)" } ?: ""),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            text = product.name
                                            product.defaultArea?.let { area = it }
                                        }
                                        .padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                if (type == AddType.ITEM) {
                    Spacer(Modifier.height(8.dp))
                    AreaInput(
                        area = area,
                        onAreaChange = {
                            area = it
                            areaExpanded = it.isNotBlank()
                        },
                        expanded = areaExpanded,
                        onExpandedChange = { areaExpanded = it },
                        areaSuggestions = areaSuggestions,
                        catalogAreas = catalogAreas
                    )
                }

                if (type == AddType.CATEGORY) {
                    Spacer(Modifier.height(16.dp))
                    Text("Farbe wählen", style = MaterialTheme.typography.labelMedium)
                    ColorPicker(selectedColor = selectedColor, onColorSelected = { selectedColor = it })

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))

                    Text("Katalog importieren (optional)", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))

                    ImportSourceSelector(
                        importSource = importSource,
                        sourceExpanded = sourceExpanded,
                        allCategories = allCategories,
                        onSourceExpandedChange = { sourceExpanded = it },
                        onImportSourceChange = { importSource = it }
                    )

                    if (importSource != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = importAreas, onCheckedChange = { importAreas = it })
                            Text("Bereiche", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.width(16.dp))
                            Checkbox(checked = importProducts, onCheckedChange = { importProducts = it })
                            Text("Produkte", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        onConfirm(
                            text,
                            if (type == AddType.CATEGORY) selectedColor else null,
                            area.ifBlank { null },
                            importSource?.id,
                            importAreas,
                            importProducts
                        )
                    }
                }
            ) {
                Text("Hinzufügen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun AreaInput(
    area: String,
    onAreaChange: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    areaSuggestions: List<CatalogArea>,
    catalogAreas: List<CatalogArea>
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = area,
            onValueChange = onAreaChange,
            label = { Text("Bereich (optional, z.B. Tiefkühl)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = { onExpandedChange(!expanded) }) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
            }
        )
        DropdownMenu(
            expanded = expanded && (areaSuggestions.isNotEmpty() || catalogAreas.isNotEmpty()),
            onDismissRequest = { onExpandedChange(false) },
            properties = PopupProperties(focusable = false)
        ) {
            val areasToDisplay = if (area.isBlank()) catalogAreas else areaSuggestions
            areasToDisplay.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion.name) },
                    onClick = {
                        onAreaChange(suggestion.name)
                        onExpandedChange(false)
                    }
                )
            }
        }
    }
}

@Composable
private fun ImportSourceSelector(
    importSource: Category?,
    sourceExpanded: Boolean,
    allCategories: List<Category>,
    onSourceExpandedChange: (Boolean) -> Unit,
    onImportSourceChange: (Category?) -> Unit
) {
    Box {
        OutlinedButton(
            onClick = { onSourceExpandedChange(true) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(importSource?.name ?: "Quelle wählen")
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(
            expanded = sourceExpanded,
            onDismissRequest = { onSourceExpandedChange(false) }
        ) {
            DropdownMenuItem(
                text = { Text("Nicht importieren") },
                onClick = {
                    onImportSourceChange(null)
                    onSourceExpandedChange(false)
                }
            )
            allCategories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = {
                        onImportSourceChange(category)
                        onSourceExpandedChange(false)
                    }
                )
            }
        }
    }
}
