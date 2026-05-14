@file:OptIn(ExperimentalMaterial3Api::class)
@file:Suppress(
    "UNUSED_VALUE",
    "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE",
    "AssignedValueIsNeverRead",
    "SameParameterValue"
)

package de.haberland.meilists

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import de.haberland.meilists.domain.filteredAndSortedItemsForDisplay
import de.haberland.meilists.domain.sortedListsForCategory
import de.haberland.meilists.model.Category
import de.haberland.meilists.model.ListItem
import de.haberland.meilists.ui.components.ListItemRow
import de.haberland.meilists.ui.dialogs.AddEntryDialog
import de.haberland.meilists.ui.dialogs.AddType
import de.haberland.meilists.ui.dialogs.CatalogManagerActions
import de.haberland.meilists.ui.dialogs.JoinCategoryDialog
import de.haberland.meilists.ui.dialogs.SettingsDialog
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
    val catalogAreas by viewModel.catalogAreas.collectAsState()
    val catalogProducts by viewModel.catalogProducts.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var showAddDialog by remember { mutableStateOf<AddType?>(null) }
    var showSettingsDialog by remember { mutableStateOf<Category?>(null) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showListMenu by remember { mutableStateOf(false) }
    var showDeleteListConfirm by remember { mutableStateOf(false) }
    var showRenameListDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ListItem?>(null) }

    val currentCategory = categories.find { it.id == selectedCategoryId }
    val categoryLists = sortedListsForCategory(lists, selectedCategoryId)
    
    val effectiveListId = selectedListId ?: categoryLists.firstOrNull()?.id
    val currentList = categoryLists.find { it.id == effectiveListId }
    
    val filteredAndSortedItems = filteredAndSortedItemsForDisplay(
        items = items,
        listId = effectiveListId,
        hideCheckedItems = currentCategory?.settings?.hideCheckedItems ?: false,
        sortByArea = currentList?.sortByArea == true
    )

    // Update-Check und UI-Events verarbeiten
    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is UiEvent.ShowToast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is UiEvent.UpdateAvailable -> {
                    val appUpdateManager = AppUpdateManagerFactory.create(context)
                    appUpdateManager.startUpdateFlowForResult(
                        event.info,
                        context as Activity,
                        AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                        1001
                    )
                }
            }
        }
    }

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
                        
                        val user = currentUser
                        if (user == null) {
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
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = user.email?.take(1)?.uppercase() ?: "U",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        style = MaterialTheme.typography.headlineSmall
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = user.displayName ?: "Benutzer",
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = user.email ?: "",
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

                // Versionsnummer ganz unten
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    effectiveListId?.let { listId ->
                            if (items.any { it.listId == listId && it.isChecked }) {
                                IconButton(onClick = { viewModel.deleteCheckedItems(listId) }) {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = "Erledigte löschen")
                                }
                            }
                            
                            Box {
                                IconButton(onClick = { showListMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Mehr")
                                }
                                DropdownMenu(
                                    expanded = showListMenu,
                                    onDismissRequest = { showListMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Liste umbenennen") },
                                        onClick = {
                                            showListMenu = false
                                            showRenameListDialog = true
                                        },
                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Nach Bereich sortieren") },
                                        onClick = {
                                            showListMenu = false
                                            viewModel.toggleSortByArea(listId)
                                        },
                                        leadingIcon = { 
                                            Icon(
                                                if (currentList?.sortByArea == true) Icons.Default.CheckCircle else Icons.AutoMirrored.Filled.Sort,
                                                contentDescription = null,
                                                tint = if (currentList?.sortByArea == true) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                            ) 
                                        }
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Liste löschen") },
                                        onClick = {
                                            showListMenu = false
                                            showDeleteListConfirm = true
                                        },
                                        leadingIcon = { 
                                            Icon(
                                                Icons.Default.DeleteForever, 
                                                contentDescription = null, 
                                                tint = MaterialTheme.colorScheme.error
                                            ) 
                                        },
                                        colors = MenuDefaults.itemColors(
                                            textColor = MaterialTheme.colorScheme.error
                                        )
                                    )
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
                if (effectiveListId != null && categoryLists.isNotEmpty()) {
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
                    val selectedIndex = categoryLists.indexOfFirst { it.id == effectiveListId }.let { if (it == -1) 0 else it }

                    PrimaryScrollableTabRow(
                        selectedTabIndex = selectedIndex,
                        edgePadding = 16.dp,
                        divider = {},
                        containerColor = Color.Transparent,
                        contentColor = currentCategory?.let { Color(it.color) } ?: MaterialTheme.colorScheme.primary
                    ) {
                        categoryLists.forEach { list ->
                            Tab(
                                selected = list.id == effectiveListId,
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
                                onCheckedChange = { viewModel.toggleItem(item.id) },
                                onEditClick = { editingItem = item },
                                onDeleteClick = { viewModel.deleteItem(item.id) }
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
            onConfirm = { name, color, area, importId, impA, impP ->
                when (type) {
                    AddType.CATEGORY -> viewModel.addCategory(name, color?.toArgb()?.toLong() ?: 0xFF6200EE, importId, impA, impP)
                    AddType.LIST -> selectedCategoryId?.let { viewModel.addList(it, name) }
                    AddType.ITEM -> effectiveListId?.let { viewModel.addItem(it, name, area) }
                }
                showAddDialog = null
            },
            catalogProducts = catalogProducts,
            catalogAreas = catalogAreas,
            allCategories = categories
        )
    }

    showSettingsDialog?.let { category ->
        SettingsDialog(
            category = category,
            onDismiss = { showSettingsDialog = null },
            onSave = { hideChecked, color, autoLearningEnabled ->
                viewModel.updateCategorySettings(category.id, hideChecked, color.toArgb().toLong(), autoLearningEnabled)
                showSettingsDialog = null
            },
            onDelete = {
                viewModel.deleteCategory(category.id)
                showSettingsDialog = null
            },
            allCategories = categories,
            catalogAreas = catalogAreas,
            catalogProducts = catalogProducts,
            catalogActions = CatalogManagerActions(
                onAddArea = { name ->
                    viewModel.addCatalogArea(category.id, name)
                },
                onRenameArea = { area, newName ->
                    viewModel.renameCatalogArea(category.id, area.id, area.name, newName)
                },
                onDeleteArea = { area ->
                    viewModel.deleteCatalogArea(category.id, area.id, area.name)
                },
                onAddProduct = { name, area ->
                    viewModel.addCatalogProduct(category.id, name, area)
                },
                onUpdateProduct = { product, name, area ->
                    viewModel.updateCatalogProduct(category.id, product.id, name, area)
                },
                onDeleteProduct = { product ->
                    viewModel.deleteCatalogProduct(category.id, product.id)
                },
                onImportCatalog = { source, importAreas, importProducts ->
                    viewModel.importCatalog(category.id, source.id, importAreas, importProducts)
                }
            )
        )
    }

    if (showDeleteListConfirm) {
        val listToDelete = categoryLists.find { it.id == effectiveListId }
        AlertDialog(
            onDismissRequest = { showDeleteListConfirm = false },
            title = { Text("Liste löschen?") },
            text = { Text("Möchtest du die Liste '${listToDelete?.name ?: ""}' wirklich löschen?") },
            confirmButton = {
                Button(
                    onClick = {
                        effectiveListId?.let { viewModel.deleteList(it) }
                        showDeleteListConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteListConfirm = false }) { Text("Abbrechen") }
            }
        )
    }

    if (showRenameListDialog) {
        val listToRename = categoryLists.find { it.id == effectiveListId }
        var newName by remember { mutableStateOf(listToRename?.name ?: "") }
        AlertDialog(
            onDismissRequest = { showRenameListDialog = false },
            title = { Text("Liste umbenennen") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Neuer Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newName.isNotBlank()) {
                        effectiveListId?.let { viewModel.renameList(it, newName) }
                        showRenameListDialog = false
                    }
                }) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameListDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    editingItem?.let { item ->
        var text by remember { mutableStateOf(item.text) }
        var area by remember { mutableStateOf(item.area ?: "") }
        AlertDialog(
            onDismissRequest = { editingItem = null },
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
                Button(onClick = {
                    if (text.isNotBlank()) {
                        viewModel.updateItem(item.id, text, area.ifBlank { null })
                        editingItem = null
                    }
                }) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(onClick = { editingItem = null }) { Text("Abbrechen") }
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
