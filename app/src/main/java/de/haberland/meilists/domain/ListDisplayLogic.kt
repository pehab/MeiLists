package de.haberland.meilists.domain

import de.haberland.meilists.model.Category
import de.haberland.meilists.model.ListItem
import de.haberland.meilists.model.ShoppingList

data class MoveItemTarget(
    val listId: String,
    val listName: String,
    val categoryId: String,
    val categoryName: String,
    val isCurrentList: Boolean
)

fun sortedListsForCategory(
    lists: List<ShoppingList>,
    categoryId: String?
): List<ShoppingList> =
    lists
        .filter { it.categoryId == categoryId }
        .sortedWith(compareByDescending<ShoppingList> { it.timestamp }.thenBy { it.name })

fun moveItemTargets(
    categories: List<Category>,
    lists: List<ShoppingList>,
    currentListId: String
): List<MoveItemTarget> {
    val categoriesById = categories.associateBy { it.id }
    val currentCategoryId = lists.find { it.id == currentListId }?.categoryId

    return lists.mapNotNull { list ->
        val category = categoriesById[list.categoryId] ?: return@mapNotNull null
        MoveItemTarget(
            listId = list.id,
            listName = list.name,
            categoryId = category.id,
            categoryName = category.name,
            isCurrentList = list.id == currentListId
        )
    }.sortedWith(
        compareBy<MoveItemTarget> { it.categoryId != currentCategoryId }
            .thenBy { it.categoryName.lowercase() }
            .thenBy { it.listName.lowercase() }
    )
}

fun filteredAndSortedItemsForDisplay(
    items: List<ListItem>,
    listId: String?,
    hideCheckedItems: Boolean,
    sortByArea: Boolean
): List<ListItem> {
    if (listId == null) return emptyList()

    return items
        .filter { it.listId == listId }
        .filter { !it.isChecked || !hideCheckedItems }
        .sortedWith { first, second ->
            if (first.isChecked != second.isChecked) {
                return@sortedWith first.isChecked.compareTo(second.isChecked)
            }

            if (sortByArea) {
                val firstArea = first.area ?: ""
                val secondArea = second.area ?: ""
                if (firstArea != secondArea) {
                    if (firstArea.isEmpty()) return@sortedWith 1
                    if (secondArea.isEmpty()) return@sortedWith -1
                    return@sortedWith firstArea.compareTo(secondArea)
                }
            }

            second.timestamp.compareTo(first.timestamp)
        }
}
