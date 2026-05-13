package de.haberland.meilists

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.haberland.meilists.model.AppDatabase
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
    fun migration11To12AddsAutoLearningDefaultAndKeepsCategoryData() = runBlocking {
        createVersion11Database()

        val migratedDatabase = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(AppDatabase.MIGRATION_11_12)
            .allowMainThreadQueries()
            .build()

        val categories = migratedDatabase.shoppingDao().getAllCategories().first()
        migratedDatabase.close()

        assertEquals(1, categories.size)
        assertEquals("Groceries", categories.single().name)
        assertFalse(categories.single().hideCheckedItems)
        assertTrue(categories.single().autoLearningEnabled)
    }

    private fun createVersion11Database() {
        val dbFile = context.getDatabasePath(TEST_DB)
        dbFile.parentFile?.mkdirs()

        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS categories (
                    id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    color INTEGER NOT NULL,
                    storageType TEXT NOT NULL,
                    remotePath TEXT,
                    hideCheckedItems INTEGER NOT NULL,
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
                    ownerId,
                    allowedUsers
                ) VALUES (
                    'cat1',
                    'Groceries',
                    4278255360,
                    'LOCAL',
                    NULL,
                    0,
                    NULL,
                    ''
                )
                """.trimIndent()
            )
            db.version = 11
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
