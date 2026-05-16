package de.haberland.meilists

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.haberland.meilists.model.AppDatabase
import de.haberland.meilists.model.CategoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    private lateinit var context: Context

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

    private suspend fun readMigratedCategories(): List<CategoryEntity> {
        val migratedDatabase = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(AppDatabase.MIGRATION_11_12, AppDatabase.MIGRATION_12_13)
            .allowMainThreadQueries()
            .build()

        val categories = migratedDatabase.shoppingDao().getAllCategories().first()
        migratedDatabase.close()
        return categories
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
                    NULL,
                    ''
                )
                """.trimIndent()
            )
            db.version = version
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
