@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package de.haberland.meilists

import android.app.Application
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.*
import de.haberland.meilists.model.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class UiEvent {
    data class ShowToast(val message: String) : UiEvent()
    data class UpdateAvailable(val info: AppUpdateInfo) : UiEvent()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).shoppingDao()
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val credentialManager = CredentialManager.create(application)
    private val crashlytics = FirebaseCrashlytics.getInstance()
    private val appUpdateManager = AppUpdateManagerFactory.create(application)

    private val activeListeners = mutableMapOf<String, ListenerRegistration>()

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    val currentUser: StateFlow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            Log.d("MeiLists", "Auth Status: ${user?.email ?: "Nicht angemeldet"}")
            trySend(user)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), auth.currentUser)

    val categories: StateFlow<List<Category>> = dao.getAllCategories()
        .map { entities ->
            entities.map { entity ->
                Category(
                    id = entity.id,
                    name = entity.name,
                    color = entity.color,
                    ownerId = entity.ownerId,
                    allowedUsers = entity.allowedUsers.split(",").filter { it.isNotBlank() },
                    settings = StorageSettings(
                        type = StorageType.valueOf(entity.storageType),
                        remotePath = entity.remotePath,
                        hideCheckedItems = entity.hideCheckedItems
                    )
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    val selectedCategoryId: StateFlow<String?> = _selectedCategoryId.asStateFlow()

    val catalogAreas: StateFlow<List<CatalogArea>> = selectedCategoryId.flatMapLatest { catId ->
        if (catId == null) flowOf(emptyList())
        else dao.getCatalogAreas(catId).map { entities ->
            entities.map { CatalogArea(it.id, it.categoryId, it.name) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val catalogProducts: StateFlow<List<CatalogProduct>> = selectedCategoryId.flatMapLatest { catId ->
        if (catId == null) flowOf(emptyList())
        else dao.getCatalogProducts(catId).map { entities ->
            entities.map { CatalogProduct(it.id, it.categoryId, it.name, it.defaultArea) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lists: StateFlow<List<ShoppingList>> = dao.getAllLists()
        .map { entities ->
            entities.map { ShoppingList(it.id, it.categoryId, it.name, it.sortByArea, it.timestamp) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedListId = MutableStateFlow<String?>(null)
    val selectedListId: StateFlow<String?> = _selectedListId.asStateFlow()

    val items: StateFlow<List<ListItem>> = dao.getAllItems()
        .map { entities ->
            entities.map { ListItem(it.id, it.listId, it.text, it.isChecked, it.timestamp, it.area) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            currentUser.collectLatest { user ->
                if (user != null) {
                    crashlytics.setUserId(user.uid)
                    syncWithFirebase()
                    // Sofort-Sync für lokal bereits bekannte Kategorien starten
                    categories.value.filter { it.settings.type == StorageType.FIREBASE }.forEach { 
                        syncListsForCategory(it.id)
                    }
                } else {
                    clearAllListeners()
                }
            }
        }

        viewModelScope.launch {
            combine(selectedCategoryId, lists) { catId, allLists ->
                catId to allLists.filter { it.categoryId == catId }
                    .sortedWith(compareByDescending<ShoppingList> { it.timestamp }.thenBy { it.name })
            }.collectLatest { (catId, catLists) ->
                if (catId != null && (_selectedListId.value == null || catLists.none { it.id == _selectedListId.value })) {
                    if (catLists.isNotEmpty()) {
                        _selectedListId.value = catLists.first().id
                    }
                }
            }
        }

        viewModelScope.launch {
            categories.collectLatest { 
                if (_selectedCategoryId.value == null && it.isNotEmpty()) {
                    selectCategory(it.first().id)
                }
            }
        }

        checkForUpdates()
    }

    private fun checkForUpdates() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && 
                info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                viewModelScope.launch { _uiEvent.emit(UiEvent.UpdateAvailable(info)) }
            }
        }
    }

    private fun clearAllListeners() {
        activeListeners.values.forEach { it.remove() }
        activeListeners.clear()
    }

    override fun onCleared() {
        super.onCleared()
        clearAllListeners()
    }

    fun signInWithGoogle(context: android.content.Context) {
        viewModelScope.launch {
            try {
                val clientId = context.getString(R.string.default_web_client_id)
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(clientId)
                    .setAutoSelectEnabled(false)
                    .build()
                val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
                val result = credentialManager.getCredential(context = context, request = request)
                val credential = result.credential
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                    auth.signInWithCredential(firebaseCredential).await()
                    _uiEvent.emit(UiEvent.ShowToast("Erfolgreich angemeldet"))
                }
            } catch (e: NoCredentialException) {
                Log.d("MeiLists", "Keine Zugangsdaten gefunden: ${e.message}")
            } catch (e: GetCredentialException) {
                Log.e("MeiLists", "Credential Fehler: ${e.message}")
                _uiEvent.emit(UiEvent.ShowToast("Anmeldefehler: ${e.message}"))
            } catch (e: Exception) {
                Log.e("MeiLists", "Login Fehler: ${e.message}")
                _uiEvent.emit(UiEvent.ShowToast("Login fehlgeschlagen"))
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
            clearAllListeners()
            _uiEvent.emit(UiEvent.ShowToast("Abgemeldet"))
        }
    }

    private fun syncWithFirebase() {
        val user = auth.currentUser ?: return
        Log.d("MeiLists", "Starte Firebase Sync für User: ${user.uid}")
        
        val categoryListener = firestore.collection("categories")
            .whereArrayContains("allowedUsers", user.uid)
            .addSnapshotListener { snapshot: QuerySnapshot?, e: FirebaseFirestoreException? ->
                if (e != null) {
                    Log.e("MeiLists", "Kategorien-Sync Fehler: ${e.message}")
                    viewModelScope.launch { _uiEvent.emit(UiEvent.ShowToast("Fehler beim Laden der Kategorien: ${e.code}")) }
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                
                snapshot.documentChanges.forEach { change ->
                    val doc = change.document
                    val id = doc.id
                    when (change.type) {
                        DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                            viewModelScope.launch {
                                dao.insertCategory(CategoryEntity(
                                    id = id,
                                    name = doc.getString("name") ?: "",
                                    color = doc.getLong("color") ?: 0L,
                                    storageType = StorageType.FIREBASE.name,
                                    remotePath = null,
                                    hideCheckedItems = doc.getBoolean("hideCheckedItems") ?: false,
                                    ownerId = doc.getString("ownerId"),
                                    allowedUsers = (doc.get("allowedUsers") as? List<*>)?.joinToString(",") ?: ""
                                ))
                                syncListsForCategory(id)
                            }
                        }
                        DocumentChange.Type.REMOVED -> {
                            viewModelScope.launch { removeLocalCategory(id) }
                        }
                    }
                }
            }
        activeListeners["categories"] = categoryListener
    }

    private suspend fun removeLocalCategory(categoryId: String) {
        activeListeners["lists_$categoryId"]?.remove()
        activeListeners.remove("lists_$categoryId")
        activeListeners["catalog_areas_$categoryId"]?.remove()
        activeListeners.remove("catalog_areas_$categoryId")
        activeListeners["catalog_products_$categoryId"]?.remove()
        activeListeners.remove("catalog_products_$categoryId")
        
        lists.value.filter { it.categoryId == categoryId }.forEach { removeLocalList(it.id) }
        dao.deleteCategory(categoryId)
    }

    private fun syncListsForCategory(categoryId: String) {
        if (activeListeners.containsKey("lists_$categoryId")) return
        syncCatalogForCategory(categoryId)
        
        val listener = firestore.collection("shopping_lists")
            .whereEqualTo("categoryId", categoryId)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                snapshot.documentChanges.forEach { change ->
                    val id = change.document.id
                    val doc = change.document
                    when (change.type) {
                        DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                            viewModelScope.launch {
                                dao.insertList(ShoppingListEntity(
                                    id = id, 
                                    categoryId = categoryId, 
                                    name = doc.getString("name") ?: "",
                                    sortByArea = doc.getBoolean("sortByArea") ?: false,
                                    timestamp = doc.getLong("timestamp") ?: 0L
                                ))
                                syncItemsForList(id)
                            }
                        }
                        DocumentChange.Type.REMOVED -> {
                            viewModelScope.launch { removeLocalList(id) }
                        }
                    }
                }
            }
        activeListeners["lists_$categoryId"] = listener
    }

    private suspend fun removeLocalList(listId: String) {
        activeListeners["items_$listId"]?.remove()
        activeListeners.remove("items_$listId")
        dao.deleteItemsByList(listId)
        dao.deleteList(listId)
    }

    private fun syncItemsForList(listId: String) {
        if (activeListeners.containsKey("items_$listId")) return
        
        val listener = firestore.collection("list_items")
            .whereEqualTo("listId", listId)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                snapshot.documentChanges.forEach { change ->
                    val id = change.document.id
                    val doc = change.document
                    when (change.type) {
                        DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                            viewModelScope.launch {
                                dao.insertItem(ListItemEntity(
                                    id = id, 
                                    listId = listId, 
                                    text = doc.getString("text") ?: "",
                                    isChecked = doc.getBoolean("isChecked") ?: false,
                                    timestamp = doc.getLong("timestamp") ?: 0L,
                                    area = doc.getString("area")
                                ))
                            }
                        }
                        DocumentChange.Type.REMOVED -> {
                            viewModelScope.launch { dao.deleteItem(id) }
                        }
                    }
                }
            }
        activeListeners["items_$listId"] = listener
    }

    fun addCategory(
        name: String, 
        color: Long, 
        importSourceId: String? = null, 
        impAreas: Boolean = false, 
        impProducts: Boolean = false
    ) {
        viewModelScope.launch {
            val user = auth.currentUser
            val id = java.util.UUID.randomUUID().toString()
            val ownerId = user?.uid
            val allowedUsers = ownerId?.let { listOf(it) } ?: emptyList()
            
            val storageType = if (user != null) StorageType.FIREBASE else StorageType.LOCAL
            
            // IMMER lokal speichern (Offline First)
            dao.insertCategory(CategoryEntity(
                id = id, 
                name = name, 
                color = color, 
                storageType = storageType.name, 
                remotePath = null, 
                hideCheckedItems = false, 
                ownerId = ownerId, 
                allowedUsers = allowedUsers.joinToString(",")
            ))

            if (user != null) {
                try {
                    firestore.collection("categories").document(id).set(hashMapOf(
                        "name" to name, 
                        "color" to color, 
                        "ownerId" to ownerId, 
                        "allowedUsers" to allowedUsers, 
                        "hideCheckedItems" to false
                    )).await()
                } catch (e: Exception) {
                    Log.e("MeiLists", "Fehler beim Firebase-Sync (Category): ${e.message}")
                }
            }
            
            if (importSourceId != null) {
                importCatalog(id, importSourceId, impAreas, impProducts)
            }

            selectCategory(id)
        }
    }

    fun deleteCategory(categoryId: String) {
        viewModelScope.launch {
            val user = auth.currentUser
            val category = categories.value.find { it.id == categoryId } ?: return@launch
            
            if (user != null && category.settings.type == StorageType.FIREBASE) {
                try {
                    if (category.ownerId == user.uid) {
                        firestore.collection("categories").document(categoryId).delete().await()
                    } else {
                        firestore.collection("categories").document(categoryId).update("allowedUsers", FieldValue.arrayRemove(user.uid)).await()
                    }
                } catch (e: Exception) {
                    Log.e("MeiLists", "Fehler beim Firebase-Delete (Category): ${e.message}")
                }
            }
            removeLocalCategory(categoryId)
            if (_selectedCategoryId.value == categoryId) selectCategory(null)
            _uiEvent.emit(UiEvent.ShowToast("Kategorie gelöscht"))
        }
    }

    fun selectCategory(id: String?) {
        _selectedCategoryId.value = id
        // Wir setzen die Listen-ID erst mal auf null, die UI nimmt sich automatisch die erste Liste
        // oder der combine-Block unten setzt sie, sobald die Daten da sind.
        _selectedListId.value = null
    }

    fun selectList(id: String?) { _selectedListId.value = id }

    private fun syncCatalogForCategory(categoryId: String) {
        if (activeListeners.containsKey("catalog_areas_$categoryId")) return

        val areaListener = firestore.collection("catalog_areas")
            .whereEqualTo("categoryId", categoryId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("MeiLists", "Katalog-Sync Fehler (Bereiche) für $categoryId: ${e.message}")
                    if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        viewModelScope.launch { _uiEvent.emit(UiEvent.ShowToast("Kein Zugriff auf Katalog-Bereiche. Bitte Firestore-Regeln prüfen.")) }
                    }
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                
                snapshot.documentChanges.forEach { change ->
                    val doc = change.document
                    when (change.type) {
                        DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                            viewModelScope.launch {
                                dao.insertCatalogArea(CatalogAreaEntity(doc.id, categoryId, doc.getString("name") ?: ""))
                            }
                        }
                        DocumentChange.Type.REMOVED -> {
                            viewModelScope.launch { dao.deleteCatalogArea(doc.id) }
                        }
                    }
                }
            }
        activeListeners["catalog_areas_$categoryId"] = areaListener

        val productListener = firestore.collection("catalog_products")
            .whereEqualTo("categoryId", categoryId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("MeiLists", "Katalog-Sync Fehler (Produkte) für $categoryId: ${e.message}")
                    if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        viewModelScope.launch { _uiEvent.emit(UiEvent.ShowToast("Kein Zugriff auf Katalog-Produkte. Bitte Firestore-Regeln prüfen.")) }
                    }
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                snapshot.documentChanges.forEach { change ->
                    val doc = change.document
                    when (change.type) {
                        DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                            viewModelScope.launch {
                                dao.insertCatalogProduct(CatalogProductEntity(
                                    doc.id, 
                                    categoryId, 
                                    doc.getString("name") ?: "",
                                    doc.getString("defaultArea")
                                ))
                            }
                        }
                        DocumentChange.Type.REMOVED -> {
                            viewModelScope.launch { dao.deleteCatalogProduct(doc.id) }
                        }
                    }
                }
            }
        activeListeners["catalog_products_$categoryId"] = productListener
    }

    fun addItem(listId: String, text: String, area: String? = null) {
        viewModelScope.launch {
            val id = java.util.UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()
            
            val list = lists.value.find { it.id == listId } ?: return@launch
            val category = categories.value.find { it.id == list.categoryId }
            
            // Auto-Learning: Produkt in den Katalog aufnehmen, falls neu
            updateCatalogWithNewItem(list.categoryId, text, area)

            // IMMER lokal speichern (Offline First)
            dao.insertItem(ListItemEntity(id, listId, text, false, timestamp, area))

            if (category?.settings?.type == StorageType.FIREBASE) {
                try {
                    firestore.collection("list_items").document(id).set(hashMapOf(
                        "listId" to listId, 
                        "text" to text, 
                        "isChecked" to false, 
                        "timestamp" to timestamp,
                        "area" to area
                    )).await()
                } catch (e: Exception) {
                    Log.e("MeiLists", "Fehler beim Firebase-Sync (Item): ${e.message}")
                }
            }
        }
    }

    private suspend fun updateCatalogWithNewItem(categoryId: String, text: String, area: String?) {
        val category = categories.value.find { it.id == categoryId }
        val isFirebase = category?.settings?.type == StorageType.FIREBASE

        // Bereich speichern, falls neu
        if (!area.isNullOrBlank()) {
            val existingArea = dao.getAreaByName(categoryId, area)
            val areaId = existingArea?.id ?: java.util.UUID.randomUUID().toString()
            
            if (existingArea == null) {
                dao.insertCatalogArea(CatalogAreaEntity(areaId, categoryId, area))
            }
            
            if (isFirebase) {
                try {
                    firestore.collection("catalog_areas").document(areaId).set(hashMapOf(
                        "categoryId" to categoryId,
                        "name" to area
                    )).await()
                } catch (e: Exception) {
                    Log.e("MeiLists", "Katalog-Sync Fehler (Area): ${e.message}")
                }
            }
        }

        // Produkt speichern
        val existingProduct = dao.getProductByName(categoryId, text)
        val productId = existingProduct?.id ?: java.util.UUID.randomUUID().toString()
        
        if (existingProduct == null) {
            dao.insertCatalogProduct(CatalogProductEntity(productId, categoryId, text, area))
        }

        if (isFirebase) {
            try {
                firestore.collection("catalog_products").document(productId).set(hashMapOf(
                    "categoryId" to categoryId,
                    "name" to text,
                    "defaultArea" to area
                )).await()
                Log.d("MeiLists", "Produkt erfolgreich nach Firebase synchronisiert: $text")
            } catch (e: Exception) {
                Log.e("MeiLists", "Katalog-Sync Fehler (Product): ${e.message}")
                _uiEvent.emit(UiEvent.ShowToast("Fehler beim Hochladen von $text"))
            }
        }
    }

    fun toggleItem(itemId: String) {
        viewModelScope.launch {
            val item = items.value.find { it.id == itemId } ?: return@launch
            val newChecked = !item.isChecked
            dao.updateItem(ListItemEntity(item.id, item.listId, item.text, newChecked, item.timestamp, item.area))
            val list = lists.value.find { it.id == item.listId }
            val category = categories.value.find { it.id == list?.categoryId }
            if (category?.settings?.type == StorageType.FIREBASE) {
                try {
                    firestore.collection("list_items").document(itemId).update("isChecked", newChecked).await()
                } catch (e: Exception) {
                    Log.e("MeiLists", "Fehler beim Firebase-Update (Item): ${e.message}")
                }
            }
        }
    }

    fun updateItem(itemId: String, newText: String, newArea: String?) {
        viewModelScope.launch {
            val item = items.value.find { it.id == itemId } ?: return@launch
            dao.updateItem(ListItemEntity(item.id, item.listId, newText, item.isChecked, item.timestamp, newArea))
            val list = lists.value.find { it.id == item.listId }
            val category = categories.value.find { it.id == list?.categoryId }
            if (category?.settings?.type == StorageType.FIREBASE) {
                try {
                    firestore.collection("list_items").document(itemId).update(mapOf(
                        "text" to newText,
                        "area" to newArea
                    )).await()
                } catch (e: Exception) {
                    Log.e("MeiLists", "Fehler beim Firebase-Update (Item): ${e.message}")
                }
            }
        }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch {
            val item = items.value.find { it.id == itemId } ?: return@launch
            dao.deleteItem(itemId)
            val list = lists.value.find { it.id == item.listId }
            val category = categories.value.find { it.id == list?.categoryId }
            if (category?.settings?.type == StorageType.FIREBASE) {
                try {
                    firestore.collection("list_items").document(itemId).delete().await()
                } catch (e: Exception) {
                    Log.e("MeiLists", "Fehler beim Firebase-Delete (Item): ${e.message}")
                }
            }
        }
    }

    fun deleteCheckedItems(listId: String) {
        viewModelScope.launch {
            val checkedItems = items.value.filter { it.listId == listId && it.isChecked }
            dao.deleteCheckedItems(listId)
            val list = lists.value.find { it.id == listId }
            val category = categories.value.find { it.id == list?.categoryId }
            if (category?.settings?.type == StorageType.FIREBASE) {
                checkedItems.forEach { firestore.collection("list_items").document(it.id).delete() }
            }
        }
    }

    fun joinCategory(inviteCode: String) {
        viewModelScope.launch {
            val user = auth.currentUser ?: return@launch
            try {
                firestore.collection("categories").document(inviteCode).update("allowedUsers", FieldValue.arrayUnion(user.uid)).await()
                _uiEvent.emit(UiEvent.ShowToast("Kategorie beigetreten"))
                selectCategory(inviteCode)
            } catch (e: Exception) {
                Log.e("MeiLists", "Join Fehler: ${e.message}")
                _uiEvent.emit(UiEvent.ShowToast("Fehler beim Beitreten: ${e.message}"))
            }
        }
    }

    fun updateCategorySettings(categoryId: String, hideChecked: Boolean, color: Long) {
        viewModelScope.launch {
            val current = categories.value.find { it.id == categoryId } ?: return@launch
            dao.updateCategory(CategoryEntity(id = categoryId, name = current.name, color = color, storageType = current.settings.type.name, remotePath = null, hideCheckedItems = hideChecked, ownerId = current.ownerId, allowedUsers = current.allowedUsers.joinToString(",")))
            if (current.settings.type == StorageType.FIREBASE) {
                firestore.collection("categories").document(categoryId).update(
                    "color", color,
                    "hideCheckedItems", hideChecked
                ).await()
            }
        }
    }

    // --- Katalog Management ---

    fun addCatalogArea(categoryId: String, name: String) {
        viewModelScope.launch {
            val id = java.util.UUID.randomUUID().toString()
            val category = categories.value.find { it.id == categoryId }
            
            // Lokal speichern
            dao.insertCatalogArea(CatalogAreaEntity(id, categoryId, name))
            
            // Firebase-Sync
            if (category?.settings?.type == StorageType.FIREBASE) {
                try {
                    firestore.collection("catalog_areas").document(id).set(hashMapOf(
                        "categoryId" to categoryId,
                        "name" to name
                    )).await()
                } catch (e: Exception) {
                    Log.e("MeiLists", "Fehler beim Firebase-Sync (CatalogArea): ${e.message}")
                }
            }
        }
    }

    fun renameCatalogArea(categoryId: String, areaId: String, oldName: String, newName: String) {
        viewModelScope.launch {
            val category = categories.value.find { it.id == categoryId }
            
            // Lokal zuerst
            dao.insertCatalogArea(CatalogAreaEntity(areaId, categoryId, newName))
            dao.updateAreaInProducts(categoryId, oldName, newName)
            dao.updateAreaInItems(categoryId, oldName, newName)
            
            if (category?.settings?.type == StorageType.FIREBASE) {
                try {
                    firestore.collection("catalog_areas").document(areaId).update("name", newName).await()
                    
                    // Kaskadierendes Update in Firebase
                    val products = firestore.collection("catalog_products")
                        .whereEqualTo("categoryId", categoryId)
                        .whereEqualTo("defaultArea", oldName).get().await()
                    products.documents.forEach { it.reference.update("defaultArea", newName) }
                } catch (e: Exception) {
                    Log.e("MeiLists", "Fehler beim Firebase-Sync (RenameArea): ${e.message}")
                }
            }
        }
    }

    fun deleteCatalogArea(categoryId: String, areaId: String, areaName: String) {
        viewModelScope.launch {
            val category = categories.value.find { it.id == categoryId }
            
            // Lokal
            dao.deleteCatalogArea(areaId)
            dao.updateAreaInProducts(categoryId, areaName, null)
            dao.updateAreaInItems(categoryId, areaName, null)
            
            if (category?.settings?.type == StorageType.FIREBASE) {
                try {
                    firestore.collection("catalog_areas").document(areaId).delete().await()
                    val products = firestore.collection("catalog_products")
                        .whereEqualTo("categoryId", categoryId)
                        .whereEqualTo("defaultArea", areaName).get().await()
                    products.documents.forEach { it.reference.update("defaultArea", null) }
                } catch (e: Exception) {
                    Log.e("MeiLists", "Fehler beim Firebase-Sync (DeleteArea): ${e.message}")
                }
            }
        }
    }

    fun addCatalogProduct(categoryId: String, name: String, area: String?) {
        viewModelScope.launch {
            val id = java.util.UUID.randomUUID().toString()
            val category = categories.value.find { it.id == categoryId }
            
            // Lokal
            dao.insertCatalogProduct(CatalogProductEntity(id, categoryId, name, area))
            
            // Firebase
            if (category?.settings?.type == StorageType.FIREBASE) {
                try {
                    firestore.collection("catalog_products").document(id).set(hashMapOf(
                        "categoryId" to categoryId,
                        "name" to name,
                        "defaultArea" to area
                    )).await()
                } catch (e: Exception) {
                    Log.e("MeiLists", "Fehler beim Firebase-Sync (AddProduct): ${e.message}")
                }
            }
        }
    }

    fun updateCatalogProduct(categoryId: String, productId: String, newName: String, newArea: String?) {
        viewModelScope.launch {
            val category = categories.value.find { it.id == categoryId }
            
            // Lokal
            dao.insertCatalogProduct(CatalogProductEntity(productId, categoryId, newName, newArea))
            
            // Firebase
            if (category?.settings?.type == StorageType.FIREBASE) {
                try {
                    firestore.collection("catalog_products").document(productId).update(mapOf(
                        "name" to newName,
                        "defaultArea" to newArea
                    )).await()
                } catch (e: Exception) {
                    Log.e("MeiLists", "Fehler beim Firebase-Sync (UpdateProduct): ${e.message}")
                }
            }
        }
    }

    fun deleteCatalogProduct(categoryId: String, productId: String) {
        viewModelScope.launch {
            val category = categories.value.find { it.id == categoryId }
            
            // Lokal
            dao.deleteCatalogProduct(productId)
            
            // Firebase
            if (category?.settings?.type == StorageType.FIREBASE) {
                try {
                    firestore.collection("catalog_products").document(productId).delete().await()
                } catch (e: Exception) {
                    Log.e("MeiLists", "Fehler beim Firebase-Sync (DeleteProduct): ${e.message}")
                }
            }
        }
    }

    fun importCatalog(targetCategoryId: String, sourceCategoryId: String, importAreas: Boolean, importProducts: Boolean) {
        viewModelScope.launch {
            val sourceCategory = categories.value.find { it.id == sourceCategoryId } ?: return@launch
            
            if (importAreas) {
                val areaNames = if (sourceCategory.settings.type == StorageType.FIREBASE) {
                    try {
                        firestore.collection("catalog_areas")
                            .whereEqualTo("categoryId", sourceCategoryId)
                            .get().await()
                            .documents.mapNotNull { it.getString("name") }
                    } catch (e: Exception) {
                        Log.e("MeiLists", "Fehler beim Importieren der Bereiche: ${e.message}")
                        emptyList()
                    }
                } else {
                    dao.getCatalogAreasSync(sourceCategoryId).map { it.name }
                }
                
                areaNames.forEach { name ->
                    if (name.isNotBlank()) addCatalogArea(targetCategoryId, name)
                }
            }
            
            if (importProducts) {
                val productData = if (sourceCategory.settings.type == StorageType.FIREBASE) {
                    try {
                        firestore.collection("catalog_products")
                            .whereEqualTo("categoryId", sourceCategoryId)
                            .get().await()
                            .documents.mapNotNull { 
                                val name = it.getString("name")
                                if (name != null) name to it.getString("defaultArea") else null
                            }
                    } catch (e: Exception) {
                        Log.e("MeiLists", "Fehler beim Importieren der Produkte: ${e.message}")
                        emptyList()
                    }
                } else {
                    dao.getCatalogProductsSync(sourceCategoryId).map { it.name to it.defaultArea }
                }
                
                productData.forEach { (name, area) ->
                    if (name.isNotBlank()) addCatalogProduct(targetCategoryId, name, area)
                }
            }
        }
    }

    fun addList(categoryId: String, name: String) {
        viewModelScope.launch {
            val id = java.util.UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()
            
            val category = categories.value.find { it.id == categoryId }
            
            // IMMER lokal speichern (Offline First)
            dao.insertList(ShoppingListEntity(id, categoryId, name, false, timestamp))

            if (category?.settings?.type == StorageType.FIREBASE) {
                try {
                    firestore.collection("shopping_lists").document(id).set(hashMapOf(
                        "categoryId" to categoryId, 
                        "name" to name,
                        "sortByArea" to false,
                        "timestamp" to timestamp
                    )).await()
                } catch (e: Exception) {
                    Log.e("MeiLists", "Fehler beim Firebase-Sync (List): ${e.message}")
                }
            }
            selectList(id)
        }
    }

    fun renameList(listId: String, newName: String) {
        viewModelScope.launch {
            val list = lists.value.find { it.id == listId } ?: return@launch
            val category = categories.value.find { it.id == list.categoryId }
            dao.insertList(ShoppingListEntity(id = listId, categoryId = list.categoryId, name = newName, sortByArea = list.sortByArea, timestamp = list.timestamp))
            if (category?.settings?.type == StorageType.FIREBASE) {
                try {
                    firestore.collection("shopping_lists").document(listId).update("name", newName).await()
                } catch (e: Exception) {
                    Log.e("MeiLists", "Fehler beim Firebase-Rename (List): ${e.message}")
                }
            }
        }
    }

    fun toggleSortByArea(listId: String) {
        viewModelScope.launch {
            val list = lists.value.find { it.id == listId } ?: return@launch
            val newValue = !list.sortByArea
            dao.insertList(ShoppingListEntity(id = listId, categoryId = list.categoryId, name = list.name, sortByArea = newValue, timestamp = list.timestamp))
            val category = categories.value.find { it.id == list.categoryId }
            if (category?.settings?.type == StorageType.FIREBASE) {
                try {
                    firestore.collection("shopping_lists").document(listId).update("sortByArea", newValue).await()
                } catch (e: Exception) {
                    Log.e("MeiLists", "Fehler beim Firebase-Update (List Sort): ${e.message}")
                }
            }
        }
    }

    fun deleteList(listId: String) {
        viewModelScope.launch {
            val list = lists.value.find { it.id == listId } ?: return@launch
            val category = categories.value.find { it.id == list.categoryId }
            if (auth.currentUser != null && category?.settings?.type == StorageType.FIREBASE) {
                try {
                    firestore.collection("shopping_lists").document(listId).delete().await()
                } catch (e: Exception) {
                    Log.e("MeiLists", "Fehler beim Firebase-Delete (List): ${e.message}")
                }
            }
            removeLocalList(listId)
            _uiEvent.emit(UiEvent.ShowToast("Liste gelöscht"))
        }
    }
}
