package de.haberland.meilists

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.haberland.meilists.model.AppDatabase
import de.haberland.meilists.model.CategoryEntity
import de.haberland.meilists.model.ListItemEntity
import de.haberland.meilists.model.ShoppingDao
import de.haberland.meilists.model.ShoppingListEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
    fun insertAndGetCategory() = runBlocking {
        val category = CategoryEntity(
            id = "cat1",
            name = "Groceries",
            color = 0xFF00FF00L,
            storageType = "LOCAL",
            remotePath = null,
            hideCheckedItems = false
        )
        dao.insertCategory(category)
        val categories = dao.getAllCategories().first()
        assertEquals(1, categories.size)
        assertEquals("Groceries", categories[0].name)
    }

    @Test
    fun insertAndGetList() = runBlocking {
        val list = ShoppingListEntity("list1", "cat1", "Weekly")
        dao.insertList(list)
        val lists = dao.getAllLists().first()
        assertEquals(1, lists.size)
        assertEquals("Weekly", lists[0].name)
    }

    @Test
    fun insertAndGetItem() = runBlocking {
        val item = ListItemEntity("item1", "list1", "Milk", false, System.currentTimeMillis())
        dao.insertItem(item)
        val items = dao.getAllItems().first()
        assertEquals(1, items.size)
        assertEquals("Milk", items[0].text)
    }

    @Test
    fun deleteCheckedItems() = runBlocking {
        val item1 = ListItemEntity("item1", "list1", "Milk", true, System.currentTimeMillis())
        val item2 = ListItemEntity("item2", "list1", "Bread", false, System.currentTimeMillis())
        dao.insertItem(item1)
        dao.insertItem(item2)
        
        dao.deleteCheckedItems("list1")
        
        val items = dao.getAllItems().first()
        assertEquals(1, items.size)
        assertEquals("Bread", items[0].text)
    }

    @Test
    fun updateItemStatus() = runBlocking {
        val item = ListItemEntity("item1", "list1", "Milk", false, System.currentTimeMillis())
        dao.insertItem(item)
        
        val updatedItem = item.copy(isChecked = true)
        dao.updateItem(updatedItem)
        
        val items = dao.getAllItems().first()
        assertTrue(items[0].isChecked)
    }
}
