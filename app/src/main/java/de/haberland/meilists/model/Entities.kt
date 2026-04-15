package de.haberland.meilists.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Long,
    val storageType: String,
    val remotePath: String?,
    val hideCheckedItems: Boolean,
    val ownerId: String? = null,
    val allowedUsers: String = "" // Als CSV String gespeichert
)

@Entity(tableName = "shopping_lists")
data class ShoppingListEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val name: String
)

@Entity(tableName = "list_items")
data class ListItemEntity(
    @PrimaryKey val id: String,
    val listId: String,
    val text: String,
    val isChecked: Boolean,
    val timestamp: Long
)
