package de.haberland.meilists

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.haberland.meilists.model.AppDatabase
import de.haberland.meilists.model.CatalogAreaEntity
import de.haberland.meilists.model.CatalogProductEntity
import de.haberland.meilists.model.CategoryEntity
import de.haberland.meilists.model.ListItemEntity
import de.haberland.meilists.model.ShoppingDao
import de.haberland.meilists.model.ShoppingListEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShoppingDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: ShoppingDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.shoppingDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertUpdateAndDeleteCategory() = runBlocking {
        dao.insertCategory(
            CategoryEntity(
                id = "cat1",
                name = "Groceries",
                color = 0xFF00FF00L,
                storageType = "LOCAL",
                remotePath = null,
                hideCheckedItems = false
            )
        )

        val inserted = dao.getAllCategories().first().single()
        assertEquals("Groceries", inserted.name)
        assertTrue(inserted.autoLearningEnabled)

        dao.updateCategory(
            inserted.copy(
                name = "Weekly Groceries",
                hideCheckedItems = true,
                autoLearningEnabled = false
            )
        )

        val updated = dao.getAllCategories().first().single()
        assertEquals("Weekly Groceries", updated.name)
        assertTrue(updated.hideCheckedItems)
        assertFalse(updated.autoLearningEnabled)

        dao.deleteCategory("cat1")

        assertTrue(dao.getAllCategories().first().isEmpty())
    }

    @Test
    fun insertAndDeleteLists() = runBlocking {
        dao.insertList(shoppingList(id = "list1", categoryId = "cat1", name = "Weekly"))
        dao.insertList(shoppingList(id = "list2", categoryId = "cat1", name = "Party"))

        assertEquals(setOf("Weekly", "Party"), dao.getAllLists().first().map { it.name }.toSet())

        dao.deleteList("list1")

        assertEquals(listOf("list2"), dao.getAllLists().first().map { it.id })
    }

    @Test
    fun deleteListsByCategoryRemovesOnlyMatchingLists() = runBlocking {
        dao.insertList(shoppingList(id = "cat1-list", categoryId = "cat1"))
        dao.insertList(shoppingList(id = "cat2-list", categoryId = "cat2"))

        dao.deleteListsByCategory("cat1")

        assertEquals(listOf("cat2-list"), dao.getAllLists().first().map { it.id })
    }

    @Test
    fun insertUpdateAndDeleteItem() = runBlocking {
        dao.insertItem(item(id = "item1", listId = "list1", text = "Milk", checked = false))

        val inserted = dao.getAllItems().first().single()
        assertEquals("Milk", inserted.text)
        assertFalse(inserted.isChecked)

        dao.updateItem(inserted.copy(text = "Oat Milk", isChecked = true, area = "Dairy"))

        val updated = dao.getAllItems().first().single()
        assertEquals("Oat Milk", updated.text)
        assertTrue(updated.isChecked)
        assertEquals("Dairy", updated.area)

        dao.deleteItem("item1")

        assertTrue(dao.getAllItems().first().isEmpty())
    }

    @Test
    fun deleteCheckedItemsRemovesOnlyCheckedItemsForList() = runBlocking {
        dao.insertItem(item(id = "checked", listId = "list1", checked = true))
        dao.insertItem(item(id = "open", listId = "list1", checked = false))
        dao.insertItem(item(id = "other-list", listId = "list2", checked = true))

        dao.deleteCheckedItems("list1")

        assertEquals(setOf("open", "other-list"), dao.getAllItems().first().map { it.id }.toSet())
    }

    @Test
    fun deleteItemsByListRemovesOnlyMatchingItems() = runBlocking {
        dao.insertItem(item(id = "list1-item", listId = "list1"))
        dao.insertItem(item(id = "list2-item", listId = "list2"))

        dao.deleteItemsByList("list1")

        assertEquals(listOf("list2-item"), dao.getAllItems().first().map { it.id })
    }

    @Test
    fun catalogAreaQueriesAndDeleteWork() = runBlocking {
        dao.insertCatalogArea(CatalogAreaEntity(id = "area1", categoryId = "cat1", name = "Produce"))
        dao.insertCatalogArea(CatalogAreaEntity(id = "area2", categoryId = "cat2", name = "Bakery"))

        assertEquals(listOf("Produce"), dao.getCatalogAreas("cat1").first().map { it.name })
        assertEquals(listOf("Produce"), dao.getCatalogAreasSync("cat1").map { it.name })
        assertEquals("area1", dao.getAreaByName("cat1", "Produce")?.id)
        assertNull(dao.getAreaByName("cat1", "Bakery"))

        dao.deleteCatalogArea("area1")

        assertTrue(dao.getCatalogAreas("cat1").first().isEmpty())
    }

    @Test
    fun catalogProductQueriesAndDeleteWork() = runBlocking {
        dao.insertCatalogProduct(CatalogProductEntity(id = "product1", categoryId = "cat1", name = "Milk", defaultArea = "Dairy"))
        dao.insertCatalogProduct(CatalogProductEntity(id = "product2", categoryId = "cat2", name = "Bread", defaultArea = "Bakery"))

        assertEquals(listOf("Milk"), dao.getCatalogProducts("cat1").first().map { it.name })
        assertEquals(listOf("Milk"), dao.getCatalogProductsSync("cat1").map { it.name })
        assertEquals("product1", dao.getProductByName("cat1", "Milk")?.id)
        assertNull(dao.getProductByName("cat1", "Bread"))

        dao.deleteCatalogProduct("product1")

        assertTrue(dao.getCatalogProducts("cat1").first().isEmpty())
    }

    @Test
    fun areaRenameUpdatesProductsAndItemsOnlyInsideCategory() = runBlocking {
        dao.insertList(shoppingList(id = "cat1-list", categoryId = "cat1"))
        dao.insertList(shoppingList(id = "cat2-list", categoryId = "cat2"))
        dao.insertCatalogProduct(CatalogProductEntity(id = "cat1-product", categoryId = "cat1", name = "Milk", defaultArea = "Old"))
        dao.insertCatalogProduct(CatalogProductEntity(id = "cat2-product", categoryId = "cat2", name = "Bread", defaultArea = "Old"))
        dao.insertItem(item(id = "cat1-item", listId = "cat1-list", area = "Old"))
        dao.insertItem(item(id = "cat2-item", listId = "cat2-list", area = "Old"))

        dao.updateAreaInProducts(categoryId = "cat1", oldName = "Old", newName = "New")
        dao.updateAreaInItems(categoryId = "cat1", oldName = "Old", newName = "New")

        val productsById = dao.getCatalogProductsSync("cat1")
            .plus(dao.getCatalogProductsSync("cat2"))
            .associateBy { it.id }
        val itemsById = dao.getAllItems().first().associateBy { it.id }

        assertEquals("New", productsById.getValue("cat1-product").defaultArea)
        assertEquals("Old", productsById.getValue("cat2-product").defaultArea)
        assertEquals("New", itemsById.getValue("cat1-item").area)
        assertEquals("Old", itemsById.getValue("cat2-item").area)
    }

    private fun shoppingList(
        id: String,
        categoryId: String = "cat1",
        name: String = id
    ) = ShoppingListEntity(
        id = id,
        categoryId = categoryId,
        name = name,
        sortByArea = false,
        timestamp = 1L
    )

    private fun item(
        id: String,
        listId: String = "list1",
        text: String = id,
        checked: Boolean = false,
        area: String? = null
    ) = ListItemEntity(
        id = id,
        listId = listId,
        text = text,
        isChecked = checked,
        timestamp = 1L,
        area = area
    )
}
