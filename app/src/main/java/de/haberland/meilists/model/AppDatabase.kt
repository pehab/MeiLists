@file:Suppress("SameParameterValue")

package de.haberland.meilists.model

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal const val APP_DATABASE_VERSION = 13

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
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "meilists_database"
                )
                    .addMigrations(MIGRATION_11_12, MIGRATION_12_13)
                    .fallbackToDestructiveMigration(false)
                    .build()
                INSTANCE = instance
                instance
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
