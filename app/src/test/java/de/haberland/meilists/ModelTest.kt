package de.haberland.meilists

import de.haberland.meilists.model.Category
import de.haberland.meilists.model.ListItem
import de.haberland.meilists.model.ShoppingList
import de.haberland.meilists.model.StorageSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class ModelTest {

    @Test
    fun testCategoryCreation() {
        val category = Category(name = "Test Category", color = 0xFF00FF00L)
        assertEquals("Test Category", category.name)
        assertEquals(0xFF00FF00L, category.color)
        assertNotNull(category.id)
        assertNotNull(category.settings)
    }

    @Test
    fun testShoppingListCreation() {
        val list = ShoppingList(categoryId = "cat1", name = "Groceries")
        assertEquals("cat1", list.categoryId)
        assertEquals("Groceries", list.name)
        assertNotNull(list.id)
    }

    @Test
    fun testListItemCreation() {
        val item = ListItem(listId = "list1", text = "Milk")
        assertEquals("list1", item.listId)
        assertEquals("Milk", item.text)
        assertFalse(item.isChecked)
        assertNotNull(item.id)
    }

    @Test
    fun testStorageSettingsDefault() {
        val settings = StorageSettings()
        assertFalse(settings.hideCheckedItems)
        assertNotNull(settings.type)
    }
}
