@file:Suppress("SameParameterValue")

package de.haberland.meilists.model

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal const val APP_DATABASE_VERSION = 14

@Database(
    entities = [CategoryEntity::class, ShoppingListEntity::class, ListItemEntity::class, CatalogAreaEntity::class, CatalogProductEntity::class],
    version = APP_DATABASE_VERSION,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun shoppingDao(): ShoppingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = builder(context, "meilists_database").build()
                INSTANCE = instance
                instance
            }
        }

        // Shared by production and migration tests. Unknown versions fail without deleting data.
        internal fun builder(context: Context, name: String): RoomDatabase.Builder<AppDatabase> =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, name)
                .addMigrations(*ALL_MIGRATIONS)

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shopping_lists ADD COLUMN sortByArea INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE list_items ADD COLUMN area TEXT")
            }
        }

        internal val MIGRATION_3_10 = object : Migration(3, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Old lists have no creation time; 0 preserves deterministic name ordering.
                // Rebuild to match the current schema without persisting an SQL default.
                db.execSQL("CREATE TABLE shopping_lists_migration_10 (id TEXT NOT NULL PRIMARY KEY, categoryId TEXT NOT NULL, name TEXT NOT NULL, sortByArea INTEGER NOT NULL, timestamp INTEGER NOT NULL)")
                db.execSQL("INSERT INTO shopping_lists_migration_10 SELECT id, categoryId, name, sortByArea, 0 FROM shopping_lists")
                db.execSQL("DROP TABLE shopping_lists")
                db.execSQL("ALTER TABLE shopping_lists_migration_10 RENAME TO shopping_lists")
            }
        }

        internal val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE catalog_areas (id TEXT NOT NULL PRIMARY KEY, categoryId TEXT NOT NULL, name TEXT NOT NULL)")
                db.execSQL("CREATE TABLE catalog_products (id TEXT NOT NULL PRIMARY KEY, categoryId TEXT NOT NULL, name TEXT NOT NULL, defaultArea TEXT)")
            }
        }

        internal val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN autoLearningEnabled INTEGER NOT NULL DEFAULT 1")
            }
        }

        internal val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val autoLearningValue = if (db.hasCategoryColumn("autoLearningEnabled")) {
                    "COALESCE(autoLearningEnabled, 1)"
                } else {
                    "1"
                }

                db.execSQL("DROP TABLE IF EXISTS categories_migration_13")
                db.execSQL(
                    """
                    CREATE TABLE categories_migration_13 (
                        id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        color INTEGER NOT NULL,
                        storageType TEXT NOT NULL,
                        remotePath TEXT,
                        hideCheckedItems INTEGER NOT NULL,
                        autoLearningEnabled INTEGER NOT NULL DEFAULT 1,
                        ownerId TEXT,
                        allowedUsers TEXT NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO categories_migration_13 (
                        id,
                        name,
                        color,
                        storageType,
                        remotePath,
                        hideCheckedItems,
                        autoLearningEnabled,
                        ownerId,
                        allowedUsers
                    )
                    SELECT
                        id,
                        name,
                        color,
                        storageType,
                        remotePath,
                        hideCheckedItems,
                        $autoLearningValue,
                        ownerId,
                        allowedUsers
                    FROM categories
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE categories")
                db.execSQL("ALTER TABLE categories_migration_13 RENAME TO categories")
            }
        }
        internal val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE list_items ADD COLUMN repeatEveryDays INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE list_items ADD COLUMN nextDueAt INTEGER DEFAULT NULL")
            }
        }

        internal val ALL_MIGRATIONS = arrayOf(
            MIGRATION_2_3, MIGRATION_3_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14
        )

    }
}

private fun SupportSQLiteDatabase.hasCategoryColumn(columnName: String): Boolean {
    query("PRAGMA table_info(categories)").use { cursor ->
        val nameIndex = cursor.getColumnIndexOrThrow("name")
        while (cursor.moveToNext()) {
            if (cursor.getString(nameIndex) == columnName) {
                return true
            }
        }
    }
    return false
}
