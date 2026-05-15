@file:OptIn(ExperimentalMaterial3Api::class)
@file:Suppress(
    "UNUSED_VALUE",
    "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE",
    "AssignedValueIsNeverRead",
    "SameParameterValue"
)

package de.haberland.meilists.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import de.haberland.meilists.MainViewModel
import de.haberland.meilists.UiEvent
import de.haberland.meilists.domain.filteredAndSortedItemsForDisplay
import de.haberland.meilists.domain.sortedListsForCategory
import de.haberland.meilists.model.ListItem
import de.haberland.meilists.ui.dialogs.AddEntryDialog
import de.haberland.meilists.ui.dialogs.AddType
import de.haberland.meilists.ui.dialogs.CatalogManagerActions
import de.haberland.meilists.ui.dialogs.DeleteListDialog
import de.haberland.meilists.ui.dialogs.EditItemDialog
import de.haberland.meilists.ui.dialogs.JoinCategoryDialog
import de.haberland.meilists.ui.dialogs.RenameListDialog
import de.haberland.meilists.ui.dialogs.SettingsDialog
import kotlinx.coroutines.launch

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
    var settingsCategoryId by remember { mutableStateOf<String?>(null) }
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
            MainNavigationDrawerContent(
                categories = categories,
                selectedCategoryId = selectedCategoryId,
                currentUser = currentUser,
                onSignIn = { viewModel.signInWithGoogle(context) },
                onSignOut = { viewModel.signOut() },
                onSelectCategory = { categoryId ->
                    viewModel.selectCategory(categoryId)
                    scope.launch { drawerState.close() }
                },
                onCategorySettingsClick = { category ->
                    settingsCategoryId = category.id
                    scope.launch { drawerState.close() }
                },
                onAddCategoryClick = {
                    showAddDialog = AddType.CATEGORY
                    scope.launch { drawerState.close() }
                },
                onJoinCategoryClick = {
                    showJoinDialog = true
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                ShoppingTopAppBar(
                    title = currentCategory?.name ?: "MeiLists",
                    categoryColor = currentCategory?.color,
                    hasActiveList = effectiveListId != null,
                    hasCheckedItems = effectiveListId?.let { listId ->
                        items.any { it.listId == listId && it.isChecked }
                    } == true,
                    sortByArea = currentList?.sortByArea == true,
                    listMenuExpanded = showListMenu,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onDeleteCheckedItems = {
                        effectiveListId?.let { viewModel.deleteCheckedItems(it) }
                    },
                    onListMenuClick = { showListMenu = true },
                    onListMenuDismiss = { showListMenu = false },
                    onRenameList = {
                        showListMenu = false
                        showRenameListDialog = true
                    },
                    onToggleSortByArea = {
                        showListMenu = false
                        effectiveListId?.let { viewModel.toggleSortByArea(it) }
                    },
                    onDeleteList = {
                        showListMenu = false
                        showDeleteListConfirm = true
                    }
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
            MainScreenContent(
                selectedCategoryId = selectedCategoryId,
                categoryLists = categoryLists,
                effectiveListId = effectiveListId,
                categoryColor = currentCategory?.color,
                items = filteredAndSortedItems,
                onSelectList = { listId -> viewModel.selectList(listId) },
                onAddListClick = { showAddDialog = AddType.LIST },
                onToggleItem = { itemId -> viewModel.toggleItem(itemId) },
                onEditItem = { item -> editingItem = item },
                onDeleteItem = { itemId -> viewModel.deleteItem(itemId) },
                modifier = Modifier.padding(innerPadding)
            )
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

    val settingsCategory = settingsCategoryId?.let { categoryId ->
        categories.find { it.id == categoryId }
    }

    settingsCategory?.let { category ->
        val settingsCatalogAreas by remember(category.id) {
            viewModel.catalogAreasFor(category.id)
        }.collectAsState(initial = emptyList())
        val settingsCatalogProducts by remember(category.id) {
            viewModel.catalogProductsFor(category.id)
        }.collectAsState(initial = emptyList())

        SettingsDialog(
            category = category,
            onDismiss = { settingsCategoryId = null },
            onSave = { hideChecked, color, autoLearningEnabled ->
                viewModel.updateCategorySettings(category.id, hideChecked, color.toArgb().toLong(), autoLearningEnabled)
                settingsCategoryId = null
            },
            onDelete = {
                viewModel.deleteCategory(category.id)
                settingsCategoryId = null
            },
            allCategories = categories,
            catalogAreas = settingsCatalogAreas,
            catalogProducts = settingsCatalogProducts,
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
        DeleteListDialog(
            listName = listToDelete?.name,
            onDismiss = { showDeleteListConfirm = false },
            onConfirm = {
                effectiveListId?.let { viewModel.deleteList(it) }
                showDeleteListConfirm = false
            }
        )
    }

    if (showRenameListDialog) {
        val listToRename = categoryLists.find { it.id == effectiveListId }
        RenameListDialog(
            initialName = listToRename?.name ?: "",
            onDismiss = { showRenameListDialog = false },
            onConfirm = { newName ->
                effectiveListId?.let { viewModel.renameList(it, newName) }
                showRenameListDialog = false
            }
        )
    }

    editingItem?.let { item ->
        EditItemDialog(
            item = item,
            onDismiss = { editingItem = null },
            onConfirm = { text, area ->
                viewModel.updateItem(item.id, text, area)
                editingItem = null
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
