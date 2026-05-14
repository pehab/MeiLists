package de.haberland.meilists.domain

import de.haberland.meilists.model.ListItem
import de.haberland.meilists.model.ShoppingList

fun sortedListsForCategory(
    lists: List<ShoppingList>,
    categoryId: String?
): List<ShoppingList> =
    lists
        .filter { it.categoryId == categoryId }
        .sortedWith(compareByDescending<ShoppingList> { it.timestamp }.thenBy { it.name })

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
