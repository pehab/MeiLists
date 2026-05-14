package de.haberland.meilists

import de.haberland.meilists.domain.filteredAndSortedItemsForDisplay
import de.haberland.meilists.domain.sortedListsForCategory
import de.haberland.meilists.model.ListItem
import de.haberland.meilists.model.ShoppingList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListDisplayLogicTest {

    @Test
    fun sortedListsForCategoryFiltersByCategoryAndSortsNewestFirstThenName() {
        val lists = listOf(
            ShoppingList(id = "old-b", categoryId = "cat1", name = "B", timestamp = 1L),
            ShoppingList(id = "new-b", categoryId = "cat1", name = "B", timestamp = 3L),
            ShoppingList(id = "other", categoryId = "cat2", name = "Other", timestamp = 9L),
            ShoppingList(id = "new-a", categoryId = "cat1", name = "A", timestamp = 3L)
        )

        val result = sortedListsForCategory(lists, "cat1")

        assertEquals(listOf("new-a", "new-b", "old-b"), result.map { it.id })
    }

    @Test
    fun sortedListsForCategoryReturnsEmptyListWhenNoCategoryIsSelected() {
        val lists = listOf(ShoppingList(id = "list1", categoryId = "cat1", name = "A"))

        val result = sortedListsForCategory(lists, null)

        assertTrue(result.isEmpty())
    }

    @Test
    fun filteredAndSortedItemsForDisplayFiltersByList() {
        val items = listOf(
            item(id = "in-list", listId = "list1", timestamp = 1L),
            item(id = "other-list", listId = "list2", timestamp = 2L)
        )

        val result = filteredAndSortedItemsForDisplay(
            items = items,
            listId = "list1",
            hideCheckedItems = false,
            sortByArea = false
        )

        assertEquals(listOf("in-list"), result.map { it.id })
    }

    @Test
    fun filteredAndSortedItemsForDisplayCanHideCheckedItems() {
        val items = listOf(
            item(id = "open", checked = false),
            item(id = "checked", checked = true)
        )

        val result = filteredAndSortedItemsForDisplay(
            items = items,
            listId = "list1",
            hideCheckedItems = true,
            sortByArea = false
        )

        assertEquals(listOf("open"), result.map { it.id })
    }

    @Test
    fun filteredAndSortedItemsForDisplaySortsOpenBeforeCheckedAndNewestFirst() {
        val items = listOf(
            item(id = "checked-new", checked = true, timestamp = 4L),
            item(id = "open-old", checked = false, timestamp = 1L),
            item(id = "open-new", checked = false, timestamp = 5L),
            item(id = "checked-old", checked = true, timestamp = 2L)
        )

        val result = filteredAndSortedItemsForDisplay(
            items = items,
            listId = "list1",
            hideCheckedItems = false,
            sortByArea = false
        )

        assertEquals(listOf("open-new", "open-old", "checked-new", "checked-old"), result.map { it.id })
    }

    @Test
    fun filteredAndSortedItemsForDisplaySortsByAreaWithBlankAreasLast() {
        val items = listOf(
            item(id = "blank", area = null, timestamp = 9L),
            item(id = "frozen", area = "Frozen", timestamp = 1L),
            item(id = "bakery-new", area = "Bakery", timestamp = 4L),
            item(id = "bakery-old", area = "Bakery", timestamp = 2L)
        )

        val result = filteredAndSortedItemsForDisplay(
            items = items,
            listId = "list1",
            hideCheckedItems = false,
            sortByArea = true
        )

        assertEquals(listOf("bakery-new", "bakery-old", "frozen", "blank"), result.map { it.id })
    }

    @Test
    fun filteredAndSortedItemsForDisplayReturnsEmptyListWhenNoListIsSelected() {
        val result = filteredAndSortedItemsForDisplay(
            items = listOf(item(id = "item1")),
            listId = null,
            hideCheckedItems = false,
            sortByArea = false
        )

        assertTrue(result.isEmpty())
    }

    private fun item(
        id: String,
        listId: String = "list1",
        checked: Boolean = false,
        timestamp: Long = 1L,
        area: String? = null
    ) = ListItem(
        id = id,
        listId = listId,
        text = id,
        isChecked = checked,
        timestamp = timestamp,
        area = area
    )
}
