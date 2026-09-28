package de.haberland.meilists

import de.haberland.meilists.domain.isVisibleTo
import de.haberland.meilists.model.CategoryEntity
import org.junit.Assert.*
import org.junit.Test

class CategoryAccessTest {
    private val cloud = CategoryEntity("id", "Shared", 0L, "FIREBASE", null, false, allowedUsers = "alice,bob")

    @Test fun cloudCacheIsHiddenAfterSignOutOrAccountSwitch() {
        assertTrue(cloud.isVisibleTo("alice"))
        assertTrue(cloud.isVisibleTo("bob"))
        assertFalse(cloud.isVisibleTo(null))
        assertFalse(cloud.isVisibleTo("charlie"))
        assertFalse(cloud.isVisibleTo("ali"))
    }

    @Test fun localDataRemainsAvailableWithoutAnAccount() {
        assertTrue(cloud.copy(storageType = "LOCAL", allowedUsers = "").isVisibleTo(null))
        assertTrue(cloud.copy(storageType = "LOCAL", allowedUsers = "").isVisibleTo("charlie"))
    }

    @Test fun removedMembershipHidesCachedCategory() {
        assertFalse(cloud.copy(allowedUsers = "bob").isVisibleTo("alice"))
    }
}
