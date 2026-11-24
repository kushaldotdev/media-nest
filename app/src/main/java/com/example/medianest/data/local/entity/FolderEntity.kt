package com.example.medianest.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "folders",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("parentId")]
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncVersion: Long = 0
)

/**
 * Returns the folder in [folders] that already uses [name] among the siblings of [parentId]
 * (a null [parentId] is the root group), or null when the name is free.
 * Names are compared case-insensitively after trimming surrounding whitespace.
 * [excludingId] is the folder being renamed, which never conflicts with itself.
 */
fun findFolderNameConflict(
    folders: List<FolderEntity>,
    name: String,
    parentId: Long?,
    excludingId: Long? = null
): FolderEntity? {
    val candidate = name.trim()
    return folders.firstOrNull { folder ->
        folder.parentId == parentId &&
            folder.id != excludingId &&
            folder.name.trim().equals(candidate, ignoreCase = true)
    }
}
