package de.haberland.meilists.model

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM categories")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity)

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :categoryId")
    suspend fun deleteCategory(categoryId: String)

    @Query("SELECT * FROM shopping_lists")
    fun getAllLists(): Flow<List<ShoppingListEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertList(list: ShoppingListEntity)

    @Query("DELETE FROM shopping_lists WHERE id = :listId")
    suspend fun deleteList(listId: String)

    @Query("DELETE FROM shopping_lists WHERE categoryId = :categoryId")
    suspend fun deleteListsByCategory(categoryId: String)

    @Query("SELECT * FROM list_items")
    fun getAllItems(): Flow<List<ListItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ListItemEntity)

    @Update
    suspend fun updateItem(item: ListItemEntity)

    @Query("DELETE FROM list_items WHERE id = :itemId")
    suspend fun deleteItem(itemId: String)

    @Query("DELETE FROM list_items WHERE listId = :listId AND isChecked = 1")
    suspend fun deleteCheckedItems(listId: String)

    @Query("DELETE FROM list_items WHERE listId = :listId")
    suspend fun deleteItemsByList(listId: String)

    // Catalog Areas
    @Query("SELECT * FROM catalog_areas WHERE categoryId = :categoryId")
    fun getCatalogAreas(categoryId: String): Flow<List<CatalogAreaEntity>>

    @Query("SELECT * FROM catalog_areas WHERE categoryId = :categoryId")
    suspend fun getCatalogAreasSync(categoryId: String): List<CatalogAreaEntity>

    @Query("SELECT * FROM catalog_areas WHERE categoryId = :categoryId AND name = :name LIMIT 1")
    suspend fun getAreaByName(categoryId: String, name: String): CatalogAreaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCatalogArea(area: CatalogAreaEntity)

    @Query("DELETE FROM catalog_areas WHERE id = :areaId")
    suspend fun deleteCatalogArea(areaId: String)

    @Query("UPDATE catalog_products SET defaultArea = :newName WHERE categoryId = :categoryId AND defaultArea = :oldName")
    suspend fun updateAreaInProducts(categoryId: String, oldName: String, newName: String?)

    @Query("UPDATE list_items SET area = :newName WHERE area = :oldName AND listId IN (SELECT id FROM shopping_lists WHERE categoryId = :categoryId)")
    suspend fun updateAreaInItems(categoryId: String, oldName: String, newName: String?)

    // Catalog Products
    @Query("SELECT * FROM catalog_products WHERE categoryId = :categoryId")
    fun getCatalogProducts(categoryId: String): Flow<List<CatalogProductEntity>>

    @Query("SELECT * FROM catalog_products WHERE categoryId = :categoryId")
    suspend fun getCatalogProductsSync(categoryId: String): List<CatalogProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCatalogProduct(product: CatalogProductEntity)

    @Query("DELETE FROM catalog_products WHERE id = :productId")
    suspend fun deleteCatalogProduct(productId: String)

    @Query("SELECT * FROM catalog_products WHERE categoryId = :categoryId AND name = :name LIMIT 1")
    suspend fun getProductByName(categoryId: String, name: String): CatalogProductEntity?
}
