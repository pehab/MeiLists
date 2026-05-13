package de.haberland.meilists

import de.haberland.meilists.model.Category
import de.haberland.meilists.model.CatalogArea
import de.haberland.meilists.model.CatalogProduct
import de.haberland.meilists.model.ListItem
import de.haberland.meilists.model.ShoppingList
import de.haberland.meilists.model.StorageSettings
import de.haberland.meilists.model.StorageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTest {

    @Test
    fun testCategoryCreation() {
        val settings = StorageSettings(
            type = StorageType.FIREBASE,
            remotePath = "categories/cat1",
            hideCheckedItems = true,
            autoLearningEnabled = false
        )
        val category = Category(
            id = "cat1",
            name = "Test Category",
            color = 0xFF00FF00L,
            ownerId = "owner",
            allowedUsers = listOf("owner", "friend"),
            settings = settings
        )

        assertEquals("cat1", category.id)
        assertEquals("Test Category", category.name)
        assertEquals(0xFF00FF00L, category.color)
        assertEquals("owner", category.ownerId)
        assertEquals(listOf("owner", "friend"), category.allowedUsers)
        assertEquals(settings, category.settings)
    }

    @Test
    fun testShoppingListCreation() {
        val list = ShoppingList(
            id = "list1",
            categoryId = "cat1",
            name = "Groceries",
            sortByArea = true,
            timestamp = 1234L
        )

        assertEquals("list1", list.id)
        assertEquals("cat1", list.categoryId)
        assertEquals("Groceries", list.name)
        assertTrue(list.sortByArea)
        assertEquals(1234L, list.timestamp)
    }

    @Test
    fun testListItemCreation() {
        val item = ListItem(
            id = "item1",
            listId = "list1",
            text = "Milk",
            isChecked = true,
            timestamp = 5678L,
            area = "Dairy"
        )

        assertEquals("item1", item.id)
        assertEquals("list1", item.listId)
        assertEquals("Milk", item.text)
        assertTrue(item.isChecked)
        assertEquals(5678L, item.timestamp)
        assertEquals("Dairy", item.area)
    }

    @Test
    fun testStorageSettingsDefault() {
        val settings = StorageSettings()
        assertFalse(settings.hideCheckedItems)
        assertTrue(settings.autoLearningEnabled)
        assertEquals(StorageType.LOCAL, settings.type)
        assertNull(settings.remotePath)
    }

    @Test
    fun testCatalogModels() {
        val area = CatalogArea(id = "area1", categoryId = "cat1", name = "Produce")
        val product = CatalogProduct(id = "product1", categoryId = "cat1", name = "Apples", defaultArea = "Produce")

        assertEquals("area1", area.id)
        assertEquals("cat1", area.categoryId)
        assertEquals("Produce", area.name)
        assertEquals("product1", product.id)
        assertEquals("cat1", product.categoryId)
        assertEquals("Apples", product.name)
        assertEquals("Produce", product.defaultArea)
    }

    @Test
    fun testGeneratedIdsArePresentForDefaultModels() {
        assertNotNull(Category(name = "Category").id)
        assertNotNull(ShoppingList(categoryId = "cat1", name = "List").id)
        assertNotNull(ListItem(listId = "list1", text = "Item").id)
        assertNotNull(CatalogArea(categoryId = "cat1", name = "Area").id)
        assertNotNull(CatalogProduct(categoryId = "cat1", name = "Product").id)
    }
}
