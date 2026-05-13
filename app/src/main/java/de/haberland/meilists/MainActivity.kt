@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@file:Suppress(
    "UNUSED_VALUE",
    "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE",
    "AssignedValueIsNeverRead",
    "SameParameterValue"
)

package de.haberland.meilists

import android.app.Activity
import android.content.ClipData
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
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
            viewModel = viewModel,
            allCategories = categories
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

@Composable
fun ListItemRow(
    item: ListItem, 
    onCheckedChange: (Boolean) -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!item.isChecked) }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = item.isChecked, onCheckedChange = onCheckedChange)
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.isChecked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                color = if (item.isChecked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
            )
            if (!item.area.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (item.isChecked) 0.3f else 1f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = item.area,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
        IconButton(onClick = onEditClick) {
            Icon(Icons.Default.Edit, contentDescription = "Bearbeiten", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onDeleteClick) {
            Icon(Icons.Default.Delete, contentDescription = "Löschen", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
        }
    }
}

enum class AddType { CATEGORY, LIST, ITEM }

@Composable
fun AddEntryDialog(
    type: AddType, 
    onDismiss: () -> Unit, 
    onConfirm: (String, Color?, String?, String?, Boolean, Boolean) -> Unit, 
    catalogProducts: List<de.haberland.meilists.model.CatalogProduct> = emptyList(),
    catalogAreas: List<de.haberland.meilists.model.CatalogArea> = emptyList(),
    allCategories: List<Category> = emptyList()
) {
    var text by remember { mutableStateOf("") }
    var area by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(Color(0xFF6200EE)) }
    
    // Import-Status
    var importSource by remember { mutableStateOf<Category?>(null) }
    var impAreas by remember { mutableStateOf(true) }
    var impProducts by remember { mutableStateOf(true) }
    var sourceExpanded by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    var areaExpanded by remember { mutableStateOf(false) }

    // Vorschlagslogik für Produkte
    val suggestions = if (type == AddType.ITEM && text.isNotBlank()) {
        catalogProducts.filter { it.name.contains(text, ignoreCase = true) && it.name != text }.take(5)
    } else emptyList()

    // Vorschlagslogik für Bereiche
    val areaSuggestions = if (type == AddType.ITEM && area.isNotBlank()) {
        catalogAreas.filter { it.name.contains(area, ignoreCase = true) && it.name != area }.take(3)
    } else emptyList()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(when(type) {
            AddType.CATEGORY -> "Kategorie hinzufügen"
            AddType.LIST -> "Liste hinzufügen"
            AddType.ITEM -> "Eintrag hinzufügen"
        }) },
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
                
                if (suggestions.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column {
                            suggestions.forEach { product ->
                                Text(
                                    text = product.name + (product.defaultArea?.let { " ($it)" } ?: ""),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { 
                                            text = product.name
                                            if (product.defaultArea != null) area = product.defaultArea
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
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = area,
                            onValueChange = { 
                                area = it
                                areaExpanded = it.isNotBlank()
                            },
                            label = { Text("Bereich (optional, z.B. Tiefkühl)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { areaExpanded = !areaExpanded }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }
                        )
                        DropdownMenu(
                            expanded = areaExpanded && (areaSuggestions.isNotEmpty() || catalogAreas.isNotEmpty()),
                            onDismissRequest = { areaExpanded = false },
                            properties = PopupProperties(focusable = false)
                        ) {
                            val listToDisplay = if (area.isBlank()) catalogAreas else areaSuggestions
                            listToDisplay.forEach { suggestion ->
                                DropdownMenuItem(
                                    text = { Text(suggestion.name) },
                                    onClick = {
                                        area = suggestion.name
                                        areaExpanded = false
                                    }
                                )
                            }
                        }
                    }
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
                    
                    Box {
                        OutlinedButton(
                            onClick = { sourceExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(importSource?.name ?: "Quelle wählen")
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = sourceExpanded,
                            onDismissRequest = { sourceExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Nicht importieren") },
                                onClick = { importSource = null; sourceExpanded = false }
                            )
                            allCategories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    onClick = { importSource = cat; sourceExpanded = false }
                                )
                            }
                        }
                    }
                    
                    if (importSource != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = impAreas, onCheckedChange = { impAreas = it })
                            Text("Bereiche", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.width(16.dp))
                            Checkbox(checked = impProducts, onCheckedChange = { impProducts = it })
                            Text("Produkte", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { 
                if (text.isNotBlank()) {
                    onConfirm(
                        text, 
                        if (type == AddType.CATEGORY) selectedColor else null, 
                        area.ifBlank { null },
                        importSource?.id,
                        impAreas,
                        impProducts
                    )
                }
            }) {
                Text("Hinzufügen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
fun SettingsDialog(
    category: Category, 
    onDismiss: () -> Unit, 
    onSave: (Boolean, Color, Boolean) -> Unit, 
    onDelete: () -> Unit,
    viewModel: MainViewModel? = null,
    allCategories: List<Category>
) {
    var hideChecked by remember { mutableStateOf(category.settings.hideCheckedItems) }
    var autoLearningEnabled by remember { mutableStateOf(category.settings.autoLearningEnabled) }
    var selectedColor by remember { mutableStateOf(Color(category.color)) }
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    var showCatalogManager by remember { mutableStateOf(false) }

    if (showCatalogManager && viewModel != null) {
        CatalogManagerDialog(
            category = category,
            onDismiss = { showCatalogManager = false },
            viewModel = viewModel,
            allCategories = allCategories
        )
    }

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
                
                if (viewModel != null) {
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
                Text("Kategorie-ID (zum Teilen):", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        category.id, 
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                clipboard.setClipEntry(
                                    ClipEntry(ClipData.newPlainText("Kategorie-ID", category.id))
                                )
                            }
                        }
                    ) {
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
fun CatalogManagerDialog(
    category: Category,
    onDismiss: () -> Unit,
    viewModel: MainViewModel,
    allCategories: List<Category>
) {
    val areas by viewModel.catalogAreas.collectAsState()
    val products by viewModel.catalogProducts.collectAsState()
    
    var showAddArea by remember { mutableStateOf(false) }
    var showAddProduct by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    
    var editingArea by remember { mutableStateOf<de.haberland.meilists.model.CatalogArea?>(null) }
    var editingProduct by remember { mutableStateOf<de.haberland.meilists.model.CatalogProduct?>(null) }
    
    var tabIndex by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxSize().padding(16.dp),
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
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(products) { product ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).clickable { editingProduct = product }) {
                                    Text(product.name, style = MaterialTheme.typography.bodyLarge)
                                    if (product.defaultArea != null) {
                                        Text(product.defaultArea, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                IconButton(onClick = { viewModel.deleteCatalogProduct(category.id, product.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                    Button(onClick = { showAddProduct = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Produkt hinzufügen")
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(areas) { area ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    area.name, 
                                    modifier = Modifier.weight(1f).clickable { editingArea = area }, 
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                IconButton(onClick = { viewModel.deleteCatalogArea(category.id, area.id, area.name) }) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                    Button(onClick = { showAddArea = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Bereich hinzufügen")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } }
    )

    if (showAddArea) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddArea = false },
            title = { Text("Bereich hinzufügen") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }) },
            confirmButton = { 
                Button(onClick = { 
                    if (name.isNotBlank()) {
                        viewModel.addCatalogArea(category.id, name)
                        showAddArea = false
                    }
                }) { Text("Hinzufügen") }
            }
        )
    }

    if (showAddProduct) {
        var name by remember { mutableStateOf("") }
        var selectedArea by remember { mutableStateOf<String?>(null) }
        var expanded by remember { mutableStateOf(false) }
        
        AlertDialog(
            onDismissRequest = { showAddProduct = false },
            title = { Text("Produkt hinzufügen") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Box {
                        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(selectedArea ?: "Bereich wählen (optional)", modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(text = { Text("Kein Bereich") }, onClick = { selectedArea = null; expanded = false })
                            areas.forEach { area ->
                                DropdownMenuItem(text = { Text(area.name) }, onClick = { selectedArea = area.name; expanded = false })
                            }
                        }
                    }
                }
            },
            confirmButton = { 
                Button(onClick = { 
                    if (name.isNotBlank()) {
                        viewModel.addCatalogProduct(category.id, name, selectedArea)
                        showAddProduct = false
                    }
                }) { Text("Hinzufügen") }
            }
        )
    }

    editingArea?.let { area ->
        var name by remember { mutableStateOf(area.name) }
        AlertDialog(
            onDismissRequest = { editingArea = null },
            title = { Text("Bereich bearbeiten") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }) },
            confirmButton = { 
                Button(onClick = { 
                    if (name.isNotBlank()) {
                        viewModel.renameCatalogArea(category.id, area.id, area.name, name)
                        editingArea = null
                    }
                }) { Text("Speichern") }
            }
        )
    }

    editingProduct?.let { product ->
        var name by remember { mutableStateOf(product.name) }
        var selectedArea by remember { mutableStateOf(product.defaultArea) }
        var expanded by remember { mutableStateOf(false) }
        
        AlertDialog(
            onDismissRequest = { editingProduct = null },
            title = { Text("Produkt bearbeiten") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Box {
                        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(selectedArea ?: "Bereich wählen (optional)", modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(text = { Text("Kein Bereich") }, onClick = { selectedArea = null; expanded = false })
                            areas.forEach { a ->
                                DropdownMenuItem(text = { Text(a.name) }, onClick = { selectedArea = a.name; expanded = false })
                            }
                        }
                    }
                }
            },
            confirmButton = { 
                Button(onClick = { 
                    if (name.isNotBlank()) {
                        viewModel.updateCatalogProduct(category.id, product.id, name, selectedArea)
                        editingProduct = null
                    }
                }) { Text("Speichern") }
            }
        )
    }

    if (showImport) {
        var selectedSource by remember { mutableStateOf<Category?>(null) }
        var impAreas by remember { mutableStateOf(true) }
        var impProducts by remember { mutableStateOf(true) }
        var expanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("Katalog importieren") },
            text = {
                Column {
                    Text("Quelle wählen:")
                    Box {
                        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedSource?.name ?: "Kategorie wählen")
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            allCategories.filter { it.id != category.id }.forEach { cat ->
                                DropdownMenuItem(text = { Text(cat.name) }, onClick = { selectedSource = cat; expanded = false })
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = impAreas, onCheckedChange = { impAreas = it })
                        Text("Bereiche importieren")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = impProducts, onCheckedChange = { impProducts = it })
                        Text("Produkte importieren")
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = selectedSource != null,
                    onClick = { 
                        selectedSource?.let { viewModel.importCatalog(category.id, it.id, impAreas, impProducts) }
                        showImport = false
                    }
                ) { Text("Importieren") }
            }
        )
    }
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
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Einladung annehmen") },
        text = {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("Kategorie-ID (Code)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
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
