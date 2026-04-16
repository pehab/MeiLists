@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package de.haberland.meilists

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.haberland.meilists.model.Category
import de.haberland.meilists.model.ListItem
import de.haberland.meilists.ui.theme.MeiListsTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MeiListsTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val categories by viewModel.categories.collectAsState()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsState()
    val lists by viewModel.lists.collectAsState()
    val selectedListId by viewModel.selectedListId.collectAsState()
    val items by viewModel.items.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var showAddDialog by remember { mutableStateOf<AddType?>(null) }
    var showSettingsDialog by remember { mutableStateOf<Category?>(null) }
    var showJoinDialog by remember { mutableStateOf(false) }

    val currentCategory = categories.find { it.id == selectedCategoryId }
    val categoryLists = lists.filter { it.categoryId == selectedCategoryId }
    
    val filteredAndSortedItems = items
        .filter { it.listId == selectedListId }
        .filter { !it.isChecked || !(currentCategory?.settings?.hideCheckedItems ?: false) }
        .sortedWith(compareBy<ListItem> { it.isChecked }.thenByDescending { it.timestamp })

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                // Header mit Benutzer-Info
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "MeiLists",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        if (currentUser == null) {
                            Button(
                                onClick = { viewModel.signInWithGoogle(context) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Mit Google anmelden")
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Kreis mit Initialen
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = currentUser?.email?.take(1)?.uppercase() ?: "U",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        style = MaterialTheme.typography.headlineSmall
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentUser?.displayName ?: "Benutzer",
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = currentUser?.email ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                
                                IconButton(onClick = { viewModel.signOut() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Logout,
                                        contentDescription = "Abmelden",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Kategorien",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                
                categories.forEach { category ->
                    NavigationDrawerItem(
                        label = { Text(category.name) },
                        selected = category.id == selectedCategoryId,
                        onClick = {
                            viewModel.selectCategory(category.id)
                            scope.launch { drawerState.close() }
                        },
                        icon = {
                            Box(modifier = Modifier.size(12.dp).background(Color(category.color), CircleShape))
                        },
                        badge = {
                            IconButton(onClick = { showSettingsDialog = category }) {
                                Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
                            }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
                
                Spacer(Modifier.height(8.dp))
                NavigationDrawerItem(
                    label = { Text("Kategorie hinzufügen") },
                    selected = false,
                    onClick = { 
                        showAddDialog = AddType.CATEGORY
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text("Einladung annehmen") },
                    selected = false,
                    onClick = { 
                        showJoinDialog = true
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Default.GroupAdd, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(currentCategory?.name ?: "MeiLists") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menü")
                        }
                    },
                    actions = {
                        if (selectedListId != null) {
                            IconButton(onClick = { viewModel.deleteList(selectedListId!!) }) {
                                Icon(Icons.Default.DeleteForever, contentDescription = "Liste löschen", tint = MaterialTheme.colorScheme.error)
                            }
                            if (items.any { it.listId == selectedListId && it.isChecked }) {
                                IconButton(onClick = { viewModel.deleteCheckedItems(selectedListId!!) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Erledigte löschen")
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = currentCategory?.let { Color(it.color).copy(alpha = 0.1f) } ?: MaterialTheme.colorScheme.surface
                    )
                )
            },
            floatingActionButton = {
                if (selectedListId != null) {
                    FloatingActionButton(
                        onClick = { showAddDialog = AddType.ITEM },
                        containerColor = currentCategory?.let { Color(it.color) } ?: MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Eintrag hinzufügen")
                    }
                }
            }
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding)) {
                if (selectedCategoryId != null) {
                    val selectedIndex = categoryLists.indexOfFirst { it.id == selectedListId }.let { if (it == -1) 0 else it }
                    
                    ScrollableTabRow(
                        selectedTabIndex = selectedIndex,
                        edgePadding = 16.dp,
                        divider = {},
                        containerColor = Color.Transparent,
                        contentColor = currentCategory?.let { Color(it.color) } ?: MaterialTheme.colorScheme.primary
                    ) {
                        categoryLists.forEach { list ->
                            Tab(
                                selected = list.id == selectedListId,
                                onClick = { viewModel.selectList(list.id) },
                                text = { Text(list.name) }
                            )
                        }
                        Tab(
                            selected = false,
                            onClick = { showAddDialog = AddType.LIST },
                            text = { 
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Liste")
                                }
                            }
                        )
                    }

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filteredAndSortedItems, key = { it.id }) { item ->
                            ListItemRow(
                                item = item,
                                onCheckedChange = { viewModel.toggleItem(item.id) }
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Bitte wähle eine Kategorie im Menü aus.")
                    }
                }
            }
        }
    }

    // Dialogs
    showAddDialog?.let { type ->
        AddEntryDialog(
            type = type,
            onDismiss = { showAddDialog = null },
            onConfirm = { name, color ->
                when (type) {
                    AddType.CATEGORY -> viewModel.addCategory(name, color?.toArgb()?.toLong() ?: 0xFF6200EE)
                    AddType.LIST -> selectedCategoryId?.let { viewModel.addList(it, name) }
                    AddType.ITEM -> selectedListId?.let { viewModel.addItem(it, name) }
                }
                showAddDialog = null
            }
        )
    }

    showSettingsDialog?.let { category ->
        SettingsDialog(
            category = category,
            onDismiss = { showSettingsDialog = null },
            onSave = { hideChecked, color ->
                viewModel.updateCategorySettings(category.id, hideChecked, color.toArgb().toLong())
                showSettingsDialog = null
            },
            onDelete = {
                viewModel.deleteCategory(category.id)
                showSettingsDialog = null
            }
        )
    }

    if (showJoinDialog) {
        JoinCategoryDialog(
            onDismiss = { showJoinDialog = false },
            onConfirm = { code ->
                viewModel.joinCategory(code)
                showJoinDialog = false
            }
        )
    }
}

@Composable
fun ListItemRow(item: ListItem, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!item.isChecked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = item.isChecked, onCheckedChange = onCheckedChange)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = item.text,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (item.isChecked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
            color = if (item.isChecked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
        )
    }
}

enum class AddType { CATEGORY, LIST, ITEM }

@Composable
fun AddEntryDialog(type: AddType, onDismiss: () -> Unit, onConfirm: (String, Color?) -> Unit) {
    var text by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(Color(0xFF6200EE)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(when(type) {
            AddType.CATEGORY -> "Kategorie hinzufügen"
            AddType.LIST -> "Liste hinzufügen"
            AddType.ITEM -> "Eintrag hinzufügen"
        }) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (type == AddType.CATEGORY) {
                    Spacer(Modifier.height(16.dp))
                    Text("Farbe wählen", style = MaterialTheme.typography.labelMedium)
                    ColorPicker(selectedColor = selectedColor, onColorSelected = { selectedColor = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (text.isNotBlank()) onConfirm(text, if (type == AddType.CATEGORY) selectedColor else null) }) {
                Text("Hinzufügen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
fun SettingsDialog(category: Category, onDismiss: () -> Unit, onSave: (Boolean, Color) -> Unit, onDelete: () -> Unit) {
    var hideChecked by remember { mutableStateOf(category.settings.hideCheckedItems) }
    var selectedColor by remember { mutableStateOf(Color(category.color)) }
    val clipboardManager = LocalClipboardManager.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Kategorie löschen?") },
            text = { Text("Möchtest du die Kategorie '${category.name}' und alle darin enthaltenen Listen wirklich löschen?") },
            confirmButton = {
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Endgültig löschen") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Abbrechen") }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Einstellungen: ${category.name}") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = hideChecked, onCheckedChange = { hideChecked = it })
                    Text("Erledigte Einträge ausblenden")
                }
                Spacer(Modifier.height(16.dp))
                Text("Farbe wählen", style = MaterialTheme.typography.labelMedium)
                ColorPicker(selectedColor = selectedColor, onColorSelected = { selectedColor = it })
                
                Spacer(Modifier.height(16.dp))
                Text("Kategorie-ID (zum Teilen):", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        category.id, 
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { clipboardManager.setText(AnnotatedString(category.id)) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "ID kopieren", modifier = Modifier.size(20.dp))
                    }
                }
                
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
            Button(onClick = { onSave(hideChecked, selectedColor) }) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
fun ColorPicker(selectedColor: Color, onColorSelected: (Color) -> Unit) {
    val colors = listOf(
        Color(0xFF6200EE), Color(0xFF03DAC5), Color(0xFFF44336),
        Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF3F51B5),
        Color(0xFF2196F3), Color(0xFF4CAF50), Color(0xFFFFEB3B),
        Color(0xFFFF9800)
    )
    
    FlowRow(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        colors.forEach { color ->
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color, CircleShape)
                    .border(
                        width = if (color == selectedColor) 3.dp else 0.dp,
                        color = if (color == selectedColor) MaterialTheme.colorScheme.outline else Color.Transparent,
                        shape = CircleShape
                    )
                    .clickable { onColorSelected(color) }
            )
        }
    }
}

@Composable
fun JoinCategoryDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Einladung annehmen") },
        text = {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("Kategorie-ID (Code)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { if (code.isNotBlank()) onConfirm(code) }) {
                Text("Beitreten")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
