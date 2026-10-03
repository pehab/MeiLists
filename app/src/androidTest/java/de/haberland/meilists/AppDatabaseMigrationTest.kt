package de.haberland.meilists

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.haberland.meilists.model.AppDatabase
import de.haberland.meilists.model.CategoryEntity
import de.haberland.meilists.model.ListItemEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    private lateinit var context: Context
    private var sourceVersion = 13

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(TEST_DB)
    }

    @After
    fun teardown() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun migration11To13AddsAutoLearningDefaultAndKeepsCategoryData() = runBlocking {
        createVersion11Database()

        val categories = readMigratedCategories()

        assertEquals(1, categories.size)
        assertEquals("Groceries", categories.single().name)
        assertFalse(categories.single().hideCheckedItems)
        assertTrue(categories.single().autoLearningEnabled)
    }

    @Test
    fun migration12To13KeepsCategoryDataFromOldVersion12Schema() = runBlocking {
        createVersion12DatabaseWithoutAutoLearningDefault()

        val categories = readMigratedCategories()

        assertEquals(1, categories.size)
        assertEquals("Groceries", categories.single().name)
        assertFalse(categories.single().hideCheckedItems)
        assertTrue(categories.single().autoLearningEnabled)
    }

    @Test
    fun migration12To13KeepsCategoryDataFromCurrentVersion12Schema() = runBlocking {
        createVersion12DatabaseWithAutoLearningDefault()

        val categories = readMigratedCategories()

        assertEquals(1, categories.size)
        assertEquals("Groceries", categories.single().name)
        assertFalse(categories.single().hideCheckedItems)
        assertTrue(categories.single().autoLearningEnabled)
    }

    @Test
    fun allHistoricalSchemasKeepListsAndItems() = runBlocking {
        for (version in listOf(2, 3, 10)) {
            context.deleteDatabase(TEST_DB)
            createDatabase(version, includeAutoLearning = false, autoLearningHasDefault = false)
            assertEquals("Groceries", readMigratedCategories().single().name)
        }
    }

    @Test
    fun disabledAutoLearningIsPreserved() = runBlocking {
        createVersion12DatabaseWithoutAutoLearningDefault()
        SQLiteDatabase.openDatabase(context.getDatabasePath(TEST_DB).path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("UPDATE categories SET autoLearningEnabled = 0")
        }
        assertFalse(readMigratedCategories().single().autoLearningEnabled)
    }

    @Test
    fun migration13To14KeepsItemsAndDisablesRecurrenceByDefault() = runBlocking {
        createDatabase(13, includeAutoLearning = true, autoLearningHasDefault = true)
        readMigratedCategories()
    }

    @Test
    fun currentSchemaReopensWithoutLosingData() = runBlocking {
        createVersion11Database()
        val first = readMigratedCategories()
        assertEquals(first, readMigratedCategories())
    }

    @Test
    fun recurringItemsKeepScheduleAcrossReopenAndBulkCleanup() = runBlocking {
        val database = AppDatabase.builder(context, TEST_DB).build()
        try {
            val dao = database.shoppingDao()
            dao.insertItem(ListItemEntity("repeat", "list", "Water plants", true, 1, null, 2, 172800001))
            dao.insertItem(ListItemEntity("ordinary", "list", "Milk", true, 1))
            dao.deleteCheckedItems("list")
            assertEquals(listOf("repeat"), dao.getAllItems().first().map { it.id })
        } finally {
            database.close()
        }
        val reopened = AppDatabase.builder(context, TEST_DB).build()
        try {
            val item = reopened.shoppingDao().getAllItems().first().single()
            assertEquals(2, item.repeatEveryDays!!)
            assertEquals(172800001L, item.nextDueAt!!)
            assertTrue(item.isChecked)
        } finally {
            reopened.close()
        }
    }

    @Test
    fun unknownVersionAndDowngradeKeepOriginalDatabase() {
        for (version in listOf(1, 4, 15)) {
            context.deleteDatabase(TEST_DB)
            createDatabase(version, includeAutoLearning = false, autoLearningHasDefault = false)
            val database = AppDatabase.builder(context, TEST_DB).allowMainThreadQueries().build()
            try {
                database.openHelper.writableDatabase
                fail("Unsupported version $version must fail without recreation")
            } catch (expected: IllegalStateException) {
                assertTrue(expected.message.orEmpty().contains("migration", ignoreCase = true))
            } finally {
                database.close()
            }
            SQLiteDatabase.openDatabase(context.getDatabasePath(TEST_DB).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                assertEquals(version, db.version)
                db.rawQuery("SELECT text FROM list_items WHERE id = 'item1'", null).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("Milk", cursor.getString(0))
                }
            }
        }
    }

    private suspend fun readMigratedCategories(): List<CategoryEntity> {
        val migratedDatabase = AppDatabase.builder(context, TEST_DB)
            .allowMainThreadQueries()
            .build()

        try {
            val dao = migratedDatabase.shoppingDao()
            val categories = dao.getAllCategories().first()
            val list = dao.getAllLists().first().single()
            assertEquals("list1", list.id)
            assertEquals("cat1", list.categoryId)
            assertEquals("Weekly", list.name)
            assertEquals(sourceVersion >= 3, list.sortByArea)
            assertEquals(if (sourceVersion >= 10) 1234L else 0L, list.timestamp)
            val items = dao.getAllItems().first()
            assertEquals(2, items.size)
            val item = items.single { it.id == "item1" }
            assertEquals(null, item.repeatEveryDays)
            assertEquals(null, item.nextDueAt)
            assertEquals("list1", item.listId)
            assertEquals("Milk", item.text)
            assertTrue(item.isChecked)
            assertEquals(5678L, item.timestamp)
            assertEquals(if (sourceVersion >= 3) "Dairy" else null, item.area)
            assertFalse(items.single { it.id == "item2" }.isChecked)
            if (sourceVersion >= 11) {
                assertEquals("Dairy", dao.getCatalogAreasSync("cat1").single().name)
                val product = dao.getCatalogProductsSync("cat1").single()
                assertEquals("Milk", product.name)
                assertEquals("Dairy", product.defaultArea)
            } else {
                assertTrue(dao.getCatalogAreasSync("cat1").isEmpty())
                assertTrue(dao.getCatalogProductsSync("cat1").isEmpty())
            }
            val category = categories.single()
            assertEquals(4278255360L, category.color)
            assertEquals("LOCAL", category.storageType)
            assertEquals("owner", category.ownerId)
            assertEquals("owner,member", category.allowedUsers)
            return categories
        } finally {
            migratedDatabase.close()
        }
    }

    private fun createVersion11Database() {
        createDatabase(version = 11, includeAutoLearning = false, autoLearningHasDefault = false)
    }

    private fun createVersion12DatabaseWithoutAutoLearningDefault() {
        createDatabase(version = 12, includeAutoLearning = true, autoLearningHasDefault = false)
    }

    private fun createVersion12DatabaseWithAutoLearningDefault() {
        createDatabase(version = 12, includeAutoLearning = true, autoLearningHasDefault = true)
    }

    private fun createDatabase(
        version: Int,
        includeAutoLearning: Boolean,
        autoLearningHasDefault: Boolean
    ) {
        sourceVersion = version
        val dbFile = context.getDatabasePath(TEST_DB)
        dbFile.parentFile?.mkdirs()

        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            val autoLearningColumn = if (includeAutoLearning) {
                val defaultClause = if (autoLearningHasDefault) " DEFAULT 1" else ""
                """
                    autoLearningEnabled INTEGER NOT NULL$defaultClause,
                """.trimIndent()
            } else {
                ""
            }
            val autoLearningColumnName = if (includeAutoLearning) {
                "autoLearningEnabled,"
            } else {
                ""
            }
            val autoLearningValue = if (includeAutoLearning) {
                "1,"
            } else {
                ""
            }

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS categories (
                    id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    color INTEGER NOT NULL,
                    storageType TEXT NOT NULL,
                    remotePath TEXT,
                    hideCheckedItems INTEGER NOT NULL,
                    $autoLearningColumn
                    ownerId TEXT,
                    allowedUsers TEXT NOT NULL,
                    PRIMARY KEY(id)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS shopping_lists (
                    id TEXT NOT NULL,
                    categoryId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    sortByArea INTEGER NOT NULL,
                    timestamp INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS list_items (
                    id TEXT NOT NULL,
                    listId TEXT NOT NULL,
                    text TEXT NOT NULL,
                    isChecked INTEGER NOT NULL,
                    timestamp INTEGER NOT NULL,
                    area TEXT,
                    PRIMARY KEY(id)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS catalog_areas (
                    id TEXT NOT NULL,
                    categoryId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    PRIMARY KEY(id)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS catalog_products (
                    id TEXT NOT NULL,
                    categoryId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    defaultArea TEXT,
                    PRIMARY KEY(id)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO categories (
                    id,
                    name,
                    color,
                    storageType,
                    remotePath,
                    hideCheckedItems,
                    $autoLearningColumnName
                    ownerId,
                    allowedUsers
                ) VALUES (
                    'cat1',
                    'Groceries',
                    4278255360,
                    'LOCAL',
                    NULL,
                    0,
                    $autoLearningValue
                    'owner',
                    'owner,member'
                )
                """.trimIndent()
            )
            db.execSQL("INSERT INTO shopping_lists VALUES ('list1', 'cat1', 'Weekly', 1, 1234)")
            db.execSQL("INSERT INTO list_items VALUES ('item1', 'list1', 'Milk', 1, 5678, 'Dairy')")
            db.execSQL("INSERT INTO list_items VALUES ('item2', 'list1', 'Bread', 0, 6789, NULL)")
            db.execSQL("INSERT INTO catalog_areas VALUES ('area1', 'cat1', 'Dairy')")
            db.execSQL("INSERT INTO catalog_products VALUES ('product1', 'cat1', 'Milk', 'Dairy')")
            // Reconstruct the exact historical columns from Entities.kt at the documented commits.
            if (version < 11) {
                db.execSQL("DROP TABLE catalog_areas")
                db.execSQL("DROP TABLE catalog_products")
            }
            if (version < 10) {
                val sorting = if (version >= 3) ", sortByArea INTEGER NOT NULL" else ""
                val sortingValue = if (version >= 3) ", sortByArea" else ""
                db.execSQL("CREATE TABLE old_lists (id TEXT NOT NULL PRIMARY KEY, categoryId TEXT NOT NULL, name TEXT NOT NULL$sorting)")
                db.execSQL("INSERT INTO old_lists SELECT id, categoryId, name$sortingValue FROM shopping_lists")
                db.execSQL("DROP TABLE shopping_lists")
                db.execSQL("ALTER TABLE old_lists RENAME TO shopping_lists")
            }
            if (version < 3) {
                db.execSQL("CREATE TABLE old_items (id TEXT NOT NULL PRIMARY KEY, listId TEXT NOT NULL, text TEXT NOT NULL, isChecked INTEGER NOT NULL, timestamp INTEGER NOT NULL)")
                db.execSQL("INSERT INTO old_items SELECT id, listId, text, isChecked, timestamp FROM list_items")
                db.execSQL("DROP TABLE list_items")
                db.execSQL("ALTER TABLE old_items RENAME TO list_items")
            }
            db.version = version
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
