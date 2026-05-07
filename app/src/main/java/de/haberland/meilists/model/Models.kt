package de.haberland.meilists.model

import java.util.UUID

enum class StorageType {
    LOCAL, FIREBASE
}

data class StorageSettings(
    val type: StorageType = StorageType.LOCAL,
    val remotePath: String? = null,
    val hideCheckedItems: Boolean = false
)

data class Category(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val color: Long = 0xFF6200EE,
    val ownerId: String? = null,
    val allowedUsers: List<String> = emptyList(),
    val settings: StorageSettings = StorageSettings()
)

data class ShoppingList(
    val id: String = UUID.randomUUID().toString(),
    val categoryId: String,
    val name: String,
    val sortByArea: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class ListItem(
    val id: String = UUID.randomUUID().toString(),
    val listId: String,
    val text: String,
    val isChecked: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val area: String? = null
)

data class CatalogArea(
    val id: String = UUID.randomUUID().toString(),
    val categoryId: String,
    val name: String
)

data class CatalogProduct(
    val id: String = UUID.randomUUID().toString(),
    val categoryId: String,
    val name: String,
    val defaultArea: String? = null
)
