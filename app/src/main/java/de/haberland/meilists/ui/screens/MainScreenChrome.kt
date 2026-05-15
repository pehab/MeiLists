@file:OptIn(ExperimentalMaterial3Api::class)

package de.haberland.meilists.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseUser
import de.haberland.meilists.BuildConfig
import de.haberland.meilists.model.Category

@Composable
fun MainNavigationDrawerContent(
    categories: List<Category>,
    selectedCategoryId: String?,
    currentUser: FirebaseUser?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onSelectCategory: (String) -> Unit,
    onCategorySettingsClick: (Category) -> Unit,
    onAddCategoryClick: () -> Unit,
    onJoinCategoryClick: () -> Unit
) {
    ModalDrawerSheet {
        DrawerHeader(
            currentUser = currentUser,
            onSignIn = onSignIn,
            onSignOut = onSignOut
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Kategorien",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        categories.forEach { category ->
            NavigationDrawerItem(
                label = { Text(category.name) },
                selected = category.id == selectedCategoryId,
                onClick = { onSelectCategory(category.id) },
                icon = {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color(category.color), CircleShape)
                    )
                },
                badge = {
                    IconButton(onClick = { onCategorySettingsClick(category) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
                    }
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }

        Spacer(Modifier.height(8.dp))
        NavigationDrawerItem(
            label = { Text("Kategorie hinzufügen") },
            selected = false,
            onClick = onAddCategoryClick,
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        NavigationDrawerItem(
            label = { Text("Einladung annehmen") },
            selected = false,
            onClick = onJoinCategoryClick,
            icon = { Icon(Icons.Default.GroupAdd, contentDescription = null) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )

        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DrawerHeader(
    currentUser: FirebaseUser?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = "MeiLists",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (currentUser == null) {
                Button(
                    onClick = onSignIn,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Mit Google anmelden")
                }
            } else {
                SignedInUserRow(
                    currentUser = currentUser,
                    onSignOut = onSignOut
                )
            }
        }
    }
}

@Composable
private fun SignedInUserRow(
    currentUser: FirebaseUser,
    onSignOut: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = currentUser.email?.take(1)?.uppercase() ?: "U",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.headlineSmall
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = currentUser.displayName ?: "Benutzer",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = currentUser.email ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onSignOut) {
            Icon(
                Icons.AutoMirrored.Filled.Logout,
                contentDescription = "Abmelden",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun ShoppingTopAppBar(
    title: String,
    categoryColor: Long?,
    hasActiveList: Boolean,
    hasCheckedItems: Boolean,
    sortByArea: Boolean,
    listMenuExpanded: Boolean,
    onOpenDrawer: () -> Unit,
    onDeleteCheckedItems: () -> Unit,
    onListMenuClick: () -> Unit,
    onListMenuDismiss: () -> Unit,
    onRenameList: () -> Unit,
    onToggleSortByArea: () -> Unit,
    onDeleteList: () -> Unit
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Default.Menu, contentDescription = "Menü")
            }
        },
        actions = {
            if (hasActiveList) {
                if (hasCheckedItems) {
                    IconButton(onClick = onDeleteCheckedItems) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Erledigte löschen")
                    }
                }

                Box {
                    IconButton(onClick = onListMenuClick) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Mehr")
                    }
                    DropdownMenu(
                        expanded = listMenuExpanded,
                        onDismissRequest = onListMenuDismiss
                    ) {
                        DropdownMenuItem(
                            text = { Text("Liste umbenennen") },
                            onClick = onRenameList,
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Nach Bereich sortieren") },
                            onClick = onToggleSortByArea,
                            leadingIcon = {
                                Icon(
                                    if (sortByArea) {
                                        Icons.Default.CheckCircle
                                    } else {
                                        Icons.AutoMirrored.Filled.Sort
                                    },
                                    contentDescription = null,
                                    tint = if (sortByArea) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        LocalContentColor.current
                                    }
                                )
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Liste löschen") },
                            onClick = onDeleteList,
                            leadingIcon = {
                                Icon(
                                    Icons.Default.DeleteForever,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            colors = MenuDefaults.itemColors(
                                textColor = MaterialTheme.colorScheme.error
                            )
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = categoryColor?.let { Color(it).copy(alpha = 0.1f) }
                ?: MaterialTheme.colorScheme.surface
        )
    )
}
