package de.haberland.meilists.domain

import de.haberland.meilists.model.CategoryEntity
import de.haberland.meilists.model.StorageType

/** Local categories remain available without an account; cloud caches belong to their members. */
internal fun CategoryEntity.isVisibleTo(userId: String?): Boolean =
    storageType == StorageType.LOCAL.name ||
        (storageType == StorageType.FIREBASE.name && userId != null &&
            allowedUsers.split(",").any { it.trim() == userId })
