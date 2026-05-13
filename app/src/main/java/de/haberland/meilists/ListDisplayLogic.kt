package de.haberland.meilists

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
        .sortedWith { a, b ->
            if (a.isChecked != b.isChecked) return@sortedWith a.isChecked.compareTo(b.isChecked)

            if (sortByArea) {
                val areaA = a.area ?: ""
                val areaB = b.area ?: ""
                if (areaA != areaB) {
                    if (areaA.isEmpty()) return@sortedWith 1
                    if (areaB.isEmpty()) return@sortedWith -1
                    return@sortedWith areaA.compareTo(areaB)
                }
            }

            b.timestamp.compareTo(a.timestamp)
        }
}
