package de.haberland.meilists

import android.app.Application
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
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
    data object ShowToast : UiEvent()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).shoppingDao()
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val credentialManager = CredentialManager.create(application)
    private val crashlytics = FirebaseCrashlytics.getInstance()

    private val activeListeners = mutableMapOf<String, ListenerRegistration>()
    
    private val _uiEvent = MutableSharedFlow<UiEvent>()

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

    val lists: StateFlow<List<ShoppingList>> = dao.getAllLists()
        .map { entities ->
            entities.map { ShoppingList(it.id, it.categoryId, it.name) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedListId = MutableStateFlow<String?>(null)
    val selectedListId: StateFlow<String?> = _selectedListId.asStateFlow()

    val items: StateFlow<List<ListItem>> = dao.getAllItems()
        .map { entities ->
            entities.map { ListItem(it.id, it.listId, it.text, it.isChecked, it.timestamp) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            currentUser.collectLatest { user ->
                if (user != null) {
                    crashlytics.setUserId(user.uid)
                    syncWithFirebase()
                } else {
                    clearAllListeners()
                }
            }
        }

        viewModelScope.launch {
            combine(selectedCategoryId, lists) { catId, allLists ->
                catId to allLists.filter { it.categoryId == catId }
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
                    _uiEvent.emit(UiEvent.ShowToast)
                }
            } catch (e: Exception) {
                Log.e("MeiLists", "Login Fehler: ${e.message}")
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
            clearAllListeners()
            _uiEvent.emit(UiEvent.ShowToast)
        }
    }

    private fun syncWithFirebase() {
        val user = auth.currentUser ?: return
        
        val categoryListener = firestore.collection("categories")
            .whereArrayContains("allowedUsers", user.uid)
            .addSnapshotListener { snapshot: QuerySnapshot?, e: FirebaseFirestoreException? ->
                if (e != null || snapshot == null) return@addSnapshotListener
                
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
        lists.value.filter { it.categoryId == categoryId }.forEach { removeLocalList(it.id) }
        dao.deleteCategory(categoryId)
    }

    private fun syncListsForCategory(categoryId: String) {
        if (activeListeners.containsKey("lists_$categoryId")) return
        
        val listener = firestore.collection("shopping_lists")
            .whereEqualTo("categoryId", categoryId)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                snapshot.documentChanges.forEach { change ->
                    val id = change.document.id
                    when (change.type) {
                        DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                            viewModelScope.launch {
                                dao.insertList(ShoppingListEntity(id = id, categoryId = categoryId, name = change.document.getString("name") ?: ""))
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
                    when (change.type) {
                        DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                            viewModelScope.launch {
                                dao.insertItem(ListItemEntity(
                                    id, listId, change.document.getString("text") ?: "",
                                    change.document.getBoolean("isChecked") ?: false,
                                    change.document.getLong("timestamp") ?: 0L
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

    fun addCategory(name: String, color: Long) {
        viewModelScope.launch {
            val user = auth.currentUser
            val id = java.util.UUID.randomUUID().toString()
            val ownerId = user?.uid
            val allowedUsers = ownerId?.let { listOf(it) } ?: emptyList()
            dao.insertCategory(CategoryEntity(id, name, color, if (user != null) StorageType.FIREBASE.name else StorageType.LOCAL.name, null, false, ownerId, allowedUsers.joinToString(",")))
            if (user != null) {
                firestore.collection("categories").document(id).set(hashMapOf("name" to name, "color" to color, "ownerId" to ownerId, "allowedUsers" to allowedUsers, "hideCheckedItems" to false)).await()
            }
            selectCategory(id)
        }
    }

    fun deleteCategory(categoryId: String) {
        viewModelScope.launch {
            val user = auth.currentUser
            val category = categories.value.find { it.id == categoryId } ?: return@launch
            
            if (user != null && category.settings.type == StorageType.FIREBASE) {
                if (category.ownerId == user.uid) {
                    firestore.collection("categories").document(categoryId).delete().await()
                } else {
                    firestore.collection("categories").document(categoryId).update("allowedUsers", FieldValue.arrayRemove(user.uid)).await()
                }
            }
            removeLocalCategory(categoryId)
            if (_selectedCategoryId.value == categoryId) selectCategory(null)
            _uiEvent.emit(UiEvent.ShowToast)
        }
    }

    fun selectCategory(id: String?) {
        _selectedCategoryId.value = id
        _selectedListId.value = null
    }

    fun selectList(id: String?) { _selectedListId.value = id }

    fun addItem(listId: String, text: String) {
        viewModelScope.launch {
            val id = java.util.UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()
            dao.insertItem(ListItemEntity(id, listId, text, false, timestamp))
            val list = lists.value.find { it.id == listId }
            val category = categories.value.find { it.id == list?.categoryId }
            if (category?.settings?.type == StorageType.FIREBASE) {
                firestore.collection("list_items").document(id).set(hashMapOf("listId" to listId, "text" to text, "isChecked" to false, "timestamp" to timestamp)).await()
            }
        }
    }

    fun toggleItem(itemId: String) {
        viewModelScope.launch {
            val item = items.value.find { it.id == itemId } ?: return@launch
            val newChecked = !item.isChecked
            dao.updateItem(ListItemEntity(item.id, item.listId, item.text, newChecked, item.timestamp))
            val list = lists.value.find { it.id == item.listId }
            val category = categories.value.find { it.id == list?.categoryId }
            if (category?.settings?.type == StorageType.FIREBASE) {
                firestore.collection("list_items").document(itemId).update("isChecked", newChecked).await()
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
                _uiEvent.emit(UiEvent.ShowToast)
                selectCategory(inviteCode)
            } catch (e: Exception) {
                Log.e("MeiLists", "Join Fehler: ${e.message}")
                _uiEvent.emit(UiEvent.ShowToast)
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

    fun addList(categoryId: String, name: String) {
        viewModelScope.launch {
            val id = java.util.UUID.randomUUID().toString()
            dao.insertList(ShoppingListEntity(id, categoryId, name))
            val category = categories.value.find { it.id == categoryId }
            if (category?.settings?.type == StorageType.FIREBASE) {
                firestore.collection("shopping_lists").document(id).set(hashMapOf("categoryId" to categoryId, "name" to name)).await()
            }
            selectList(id)
        }
    }

    fun deleteList(listId: String) {
        viewModelScope.launch {
            val list = lists.value.find { it.id == listId } ?: return@launch
            val category = categories.value.find { it.id == list.categoryId }
            if (auth.currentUser != null && category?.settings?.type == StorageType.FIREBASE) {
                firestore.collection("shopping_lists").document(listId).delete().await()
            }
            removeLocalList(listId)
            _uiEvent.emit(UiEvent.ShowToast)
        }
    }
}
