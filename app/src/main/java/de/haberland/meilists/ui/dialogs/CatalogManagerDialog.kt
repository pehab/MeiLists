@file:OptIn(ExperimentalMaterial3Api::class)
@file:Suppress(
    "UNUSED_VALUE",
    "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE",
    "AssignedValueIsNeverRead"
)

package de.haberland.meilists.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import de.haberland.meilists.model.CatalogArea
import de.haberland.meilists.model.CatalogProduct
import de.haberland.meilists.model.Category

data class CatalogManagerActions(
    val onAddArea: (String) -> Unit,
    val onRenameArea: (CatalogArea, String) -> Unit,
    val onDeleteArea: (CatalogArea) -> Unit,
    val onAddProduct: (String, String?) -> Unit,
    val onUpdateProduct: (CatalogProduct, String, String?) -> Unit,
    val onDeleteProduct: (CatalogProduct) -> Unit,
    val onImportCatalog: (Category, Boolean, Boolean) -> Unit
)

@Composable
fun CatalogManagerDialog(
    category: Category,
    areas: List<CatalogArea>,
    products: List<CatalogProduct>,
    allCategories: List<Category>,
    actions: CatalogManagerActions,
    onDismiss: () -> Unit
) {
    var showAddArea by remember { mutableStateOf(false) }
    var showAddProduct by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var editingArea by remember { mutableStateOf<CatalogArea?>(null) }
    var editingProduct by remember { mutableStateOf<CatalogProduct?>(null) }
    var tabIndex by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Katalog: ${category.name}", modifier = Modifier.weight(1f))
                IconButton(onClick = { showImport = true }) {
                    Icon(Icons.Default.CloudDownload, contentDescription = "Importieren")
                }
            }
        },
        text = {
            Column {
                PrimaryTabRow(selectedTabIndex = tabIndex) {
                    Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("Produkte") })
                    Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("Bereiche") })
                }

                Spacer(Modifier.height(8.dp))

                if (tabIndex == 0) {
                    CatalogProductsTab(
                        products = products,
                        onEditProduct = { editingProduct = it },
                        onDeleteProduct = actions.onDeleteProduct,
                        onAddProductClick = { showAddProduct = true }
                    )
                } else {
                    CatalogAreasTab(
                        areas = areas,
                        onEditArea = { editingArea = it },
                        onDeleteArea = actions.onDeleteArea,
                        onAddAreaClick = { showAddArea = true }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Schließen") }
        }
    )

    if (showAddArea) {
        AddAreaDialog(
            onDismiss = { showAddArea = false },
            onConfirm = { name ->
                actions.onAddArea(name)
                showAddArea = false
            }
        )
    }

    if (showAddProduct) {
        CatalogProductEditorDialog(
            title = "Produkt hinzufügen",
            initialName = "",
            initialArea = null,
            areas = areas,
            onDismiss = { showAddProduct = false },
            onConfirm = { name, selectedArea ->
                actions.onAddProduct(name, selectedArea)
                showAddProduct = false
            }
        )
    }

    editingArea?.let { area ->
        EditAreaDialog(
            area = area,
            onDismiss = { editingArea = null },
            onConfirm = { newName ->
                actions.onRenameArea(area, newName)
                editingArea = null
            }
        )
    }

    editingProduct?.let { product ->
        CatalogProductEditorDialog(
            title = "Produkt bearbeiten",
            initialName = product.name,
            initialArea = product.defaultArea,
            areas = areas,
            onDismiss = { editingProduct = null },
            onConfirm = { name, selectedArea ->
                actions.onUpdateProduct(product, name, selectedArea)
                editingProduct = null
            }
        )
    }

    if (showImport) {
        ImportCatalogDialog(
            category = category,
            allCategories = allCategories,
            onDismiss = { showImport = false },
            onConfirm = { source, importAreas, importProducts ->
                actions.onImportCatalog(source, importAreas, importProducts)
                showImport = false
            }
        )
    }
}

@Composable
private fun ColumnScope.CatalogProductsTab(
    products: List<CatalogProduct>,
    onEditProduct: (CatalogProduct) -> Unit,
    onDeleteProduct: (CatalogProduct) -> Unit,
    onAddProductClick: () -> Unit
) {
    LazyColumn(modifier = Modifier.weight(1f)) {
        items(products) { product ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onEditProduct(product) }
                ) {
                    Text(product.name, style = MaterialTheme.typography.bodyLarge)
                    product.defaultArea?.let { area ->
                        Text(area, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                IconButton(onClick = { onDeleteProduct(product) }) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                }
            }
            HorizontalDivider()
        }
    }
    Button(onClick = onAddProductClick, modifier = Modifier.fillMaxWidth()) {
        Text("Produkt hinzufügen")
    }
}

@Composable
private fun ColumnScope.CatalogAreasTab(
    areas: List<CatalogArea>,
    onEditArea: (CatalogArea) -> Unit,
    onDeleteArea: (CatalogArea) -> Unit,
    onAddAreaClick: () -> Unit
) {
    LazyColumn(modifier = Modifier.weight(1f)) {
        items(areas) { area ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    area.name,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onEditArea(area) },
                    style = MaterialTheme.typography.bodyLarge
                )
                IconButton(onClick = { onDeleteArea(area) }) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                }
            }
            HorizontalDivider()
        }
    }
    Button(onClick = onAddAreaClick, modifier = Modifier.fillMaxWidth()) {
        Text("Bereich hinzufügen")
    }
}

@Composable
private fun AddAreaDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bereich hinzufügen") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") }
            )
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onConfirm(name) }) {
                Text("Hinzufügen")
            }
        }
    )
}

@Composable
private fun EditAreaDialog(
    area: CatalogArea,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(area.id) { mutableStateOf(area.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bereich bearbeiten") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") }
            )
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onConfirm(name) }) {
                Text("Speichern")
            }
        }
    )
}

@Composable
private fun CatalogProductEditorDialog(
    title: String,
    initialName: String,
    initialArea: String?,
    areas: List<CatalogArea>,
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit
) {
    var name by remember(title, initialName) { mutableStateOf(initialName) }
    var selectedArea by remember(title, initialArea) { mutableStateOf(initialArea) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                AreaSelector(
                    selectedArea = selectedArea,
                    areas = areas,
                    onAreaSelected = { selectedArea = it }
                )
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onConfirm(name, selectedArea) }) {
                Text(if (initialName.isBlank()) "Hinzufügen" else "Speichern")
            }
        }
    )
}

@Composable
private fun AreaSelector(
    selectedArea: String?,
    areas: List<CatalogArea>,
    onAreaSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(selectedArea ?: "Bereich wählen (optional)", modifier = Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Kein Bereich") },
                onClick = {
                    onAreaSelected(null)
                    expanded = false
                }
            )
            areas.forEach { area ->
                DropdownMenuItem(
                    text = { Text(area.name) },
                    onClick = {
                        onAreaSelected(area.name)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ImportCatalogDialog(
    category: Category,
    allCategories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (Category, Boolean, Boolean) -> Unit
) {
    var selectedSource by remember { mutableStateOf<Category?>(null) }
    var importAreas by remember { mutableStateOf(true) }
    var importProducts by remember { mutableStateOf(true) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Katalog importieren") },
        text = {
            Column {
                Text("Quelle wählen:")
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedSource?.name ?: "Kategorie wählen")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        allCategories
                            .filter { it.id != category.id }
                            .forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.name) },
                                    onClick = {
                                        selectedSource = category
                                        expanded = false
                                    }
                                )
                            }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = importAreas, onCheckedChange = { importAreas = it })
                    Text("Bereiche importieren")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = importProducts, onCheckedChange = { importProducts = it })
                    Text("Produkte importieren")
                }
            }
        },
        confirmButton = {
            Button(
                enabled = selectedSource != null,
                onClick = {
                    selectedSource?.let { onConfirm(it, importAreas, importProducts) }
                }
            ) {
                Text("Importieren")
            }
        }
    )
}
