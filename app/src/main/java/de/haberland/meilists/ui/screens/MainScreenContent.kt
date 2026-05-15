@file:OptIn(ExperimentalMaterial3Api::class)

package de.haberland.meilists.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.haberland.meilists.model.ListItem
import de.haberland.meilists.model.ShoppingList
import de.haberland.meilists.ui.components.ListItemRow

@Composable
fun MainScreenContent(
    selectedCategoryId: String?,
    categoryLists: List<ShoppingList>,
    effectiveListId: String?,
    categoryColor: Long?,
    items: List<ListItem>,
    onSelectList: (String) -> Unit,
    onAddListClick: () -> Unit,
    onToggleItem: (String) -> Unit,
    onEditItem: (ListItem) -> Unit,
    onDeleteItem: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        if (selectedCategoryId != null) {
            ShoppingListTabs(
                categoryLists = categoryLists,
                effectiveListId = effectiveListId,
                categoryColor = categoryColor,
                onSelectList = onSelectList,
                onAddListClick = onAddListClick
            )
            ShoppingItemsList(
                items = items,
                onToggleItem = onToggleItem,
                onEditItem = onEditItem,
                onDeleteItem = onDeleteItem
            )
        } else {
            EmptyCategorySelection()
        }
    }
}

@Composable
private fun ShoppingListTabs(
    categoryLists: List<ShoppingList>,
    effectiveListId: String?,
    categoryColor: Long?,
    onSelectList: (String) -> Unit,
    onAddListClick: () -> Unit
) {
    val selectedIndex = selectedListIndex(categoryLists, effectiveListId)

    PrimaryScrollableTabRow(
        selectedTabIndex = selectedIndex,
        edgePadding = 16.dp,
        divider = {},
        containerColor = Color.Transparent,
        contentColor = categoryColor?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    ) {
        categoryLists.forEach { list ->
            Tab(
                selected = list.id == effectiveListId,
                onClick = { onSelectList(list.id) },
                text = { Text(list.name) }
            )
        }
        Tab(
            selected = false,
            onClick = onAddListClick,
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Liste")
                }
            }
        )
    }
}

@Composable
private fun ShoppingItemsList(
    items: List<ListItem>,
    onToggleItem: (String) -> Unit,
    onEditItem: (ListItem) -> Unit,
    onDeleteItem: (String) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items, key = { it.id }) { item ->
            ListItemRow(
                item = item,
                onCheckedChange = { onToggleItem(item.id) },
                onEditClick = { onEditItem(item) },
                onDeleteClick = { onDeleteItem(item.id) }
            )
        }
    }
}

@Composable
private fun EmptyCategorySelection() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Bitte wähle eine Kategorie im Menü aus.")
    }
}

private fun selectedListIndex(
    categoryLists: List<ShoppingList>,
    effectiveListId: String?
): Int {
    return categoryLists.indexOfFirst { it.id == effectiveListId }
        .let { index -> if (index == -1) 0 else index }
}
