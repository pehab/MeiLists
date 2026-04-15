package de.haberland.meilists

import android.app.Application
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QuerySnapshot
import de.haberland.meilists.model.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).shoppingDao()
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val credentialManager = CredentialManager.create(application)
    private val crashlytics = FirebaseCrashlytics.getInstance()

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
                }
            }
        }

        viewModelScope.launch {
            categories.collectLatest { 
                if (_selectedCategoryId.value == null && it.isNotEmpty()) {
                    _selectedCategoryId.value = it.first().id
                }
            }
        }
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

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(
                    context = context,
                    request = request
                )

                val credential = result.credential
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                    auth.signInWithCredential(firebaseCredential).await()
                }
            } catch (e: NoCredentialException) {
                Log.e("MeiLists", "Keine Konten gefunden.")
            } catch (e: GetCredentialCancellationException) {
                // Nutzer hat abgebrochen
            } catch (e: GetCredentialException) {
                Log.e("MeiLists", "Credential Manager Fehler: ${e.message}")
            } catch (e: Exception) {
                Log.e("MeiLists", "Login Fehler: ${e.message}")
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        }
    }

    private fun syncWithFirebase() {
        val user = auth.currentUser ?: return
        
        firestore.collection("categories")
            .whereArrayContains("allowedUsers", user.uid)
            .addSnapshotListener { snapshot: QuerySnapshot?, e: FirebaseFirestoreException? ->
                if (e != null || snapshot == null) {
                    crashlytics.recordException(e ?: Exception("Firestore Snapshot Fehler"))
                    return@addSnapshotListener
                }
                
                snapshot.documents.forEach { doc: DocumentSnapshot ->
                    viewModelScope.launch {
                        val id = doc.id
                        val name = doc.getString("name") ?: ""
                        val color = doc.getLong("color") ?: 0L
                        val ownerId = doc.getString("ownerId")
                        val allowedUsers = (doc.get("allowedUsers") as? List<*>)?.joinToString(",") ?: ""
                        
                        dao.insertCategory(CategoryEntity(
                            id = id,
                            name = name,
                            color = color,
                            storageType = StorageType.FIREBASE.name,
                            remotePath = null,
                            hideCheckedItems = false,
                            ownerId = ownerId,
                            allowedUsers = allowedUsers
                        ))
                    }
                }
            }
    }

    fun addCategory(name: String, color: Long) {
        viewModelScope.launch {
            val user = auth.currentUser
            val id = java.util.UUID.randomUUID().toString()
            val ownerId = user?.uid
            val allowedUsers = ownerId?.let { listOf(it) } ?: emptyList()

            val entity = CategoryEntity(
                id = id,
                name = name,
                color = color,
                storageType = if (user != null) StorageType.FIREBASE.name else StorageType.LOCAL.name,
                remotePath = null,
                hideCheckedItems = false,
                ownerId = ownerId,
                allowedUsers = allowedUsers.joinToString(",")
            )
            
            dao.insertCategory(entity)

            if (user != null) {
                val data = hashMapOf(
                    "name" to name,
                    "color" to color,
                    "ownerId" to ownerId,
                    "allowedUsers" to allowedUsers
                )
                firestore.collection("categories").document(id).set(data).await()
            }

            if (_selectedCategoryId.value == null) {
                _selectedCategoryId.value = id
            }
        }
    }

    fun selectCategory(id: String) { _selectedCategoryId.value = id }
    fun selectList(id: String) { _selectedListId.value = id }

    fun addList(categoryId: String, name: String) {
        viewModelScope.launch {
            val id = java.util.UUID.randomUUID().toString()
            dao.insertList(ShoppingListEntity(id, categoryId, name))
            
            val category = categories.value.find { it.id == categoryId }
            if (category?.settings?.type == StorageType.FIREBASE) {
                firestore.collection("shopping_lists").document(id).set(hashMapOf(
                    "categoryId" to categoryId,
                    "name" to name
                )).await()
            }
        }
    }

    fun addItem(listId: String, text: String) {
        viewModelScope.launch {
            val id = java.util.UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()
            dao.insertItem(ListItemEntity(id, listId, text, false, timestamp))

            val list = lists.value.find { it.id == listId }
            val category = categories.value.find { it.id == list?.categoryId }
            if (category?.settings?.type == StorageType.FIREBASE) {
                firestore.collection("list_items").document(id).set(hashMapOf(
                    "listId" to listId,
                    "text" to text,
                    "isChecked" to false,
                    "timestamp" to timestamp
                )).await()
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
                checkedItems.forEach { 
                    firestore.collection("list_items").document(it.id).delete().await()
                }
            }
        }
    }

    fun joinCategory(inviteCode: String) {
        viewModelScope.launch {
            val user = auth.currentUser ?: return@launch
            try {
                val doc = firestore.collection("categories").document(inviteCode).get().await()
                if (doc.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val allowedUsers = (doc.get("allowedUsers") as? List<String>)?.toMutableList() ?: mutableListOf()
                    if (!allowedUsers.contains(user.uid)) {
                        allowedUsers.add(user.uid)
                        firestore.collection("categories").document(inviteCode).update("allowedUsers", allowedUsers).await()
                    }
                }
            } catch (e: Exception) {
                crashlytics.recordException(e)
            }
        }
    }

    fun updateCategorySettings(categoryId: String, hideChecked: Boolean, color: Long) {
        viewModelScope.launch {
            val current = categories.value.find { it.id == categoryId } ?: return@launch
            dao.updateCategory(CategoryEntity(
                id = categoryId,
                name = current.name,
                color = color,
                storageType = current.settings.type.name,
                remotePath = null,
                hideCheckedItems = hideChecked,
                ownerId = current.ownerId,
                allowedUsers = current.allowedUsers.joinToString(",")
            ))
            if (current.settings.type == StorageType.FIREBASE) {
                firestore.collection("categories").document(categoryId).update("color", color).await()
            }
        }
    }
}
