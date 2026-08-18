package com.example.medianest.data.repository

import com.example.medianest.data.local.dao.FolderDao
import com.example.medianest.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Node in a hierarchical folder tree structure.
 *
 * @property folder The [FolderEntity] represented by this node.
 * @property children The list of direct child nodes.
 */
data class FolderTreeNode(
    val folder: FolderEntity,
    val children: List<FolderTreeNode> = emptyList()
) {
    /**
     * Flattens this node and all descendants in depth-first order.
     * Returns a list of (folder, depth) pairs, starting at [startingDepth] (default 0).
     */
    fun flattenWithDepth(startingDepth: Int = 0): List<Pair<FolderEntity, Int>> {
        val result = ArrayList<Pair<FolderEntity, Int>>()
        result.add(folder to startingDepth)
        for (child in children) {
            result.addAll(child.flattenWithDepth(startingDepth + 1))
        }
        return result
    }
}

/**
 * Extension helper to flatten a forest/list of root [FolderTreeNode]s in depth-first order.
 * Returns a list of (folder, depth) pairs where roots have depth [startingDepth] (default 0).
 */
fun List<FolderTreeNode>.flattenWithDepth(startingDepth: Int = 0): List<Pair<FolderEntity, Int>> {
    val result = ArrayList<Pair<FolderEntity, Int>>()
    for (node in this) {
        result.addAll(node.flattenWithDepth(startingDepth))
    }
    return result
}

@Singleton
class FolderRepository @Inject constructor(
    private val folderDao: FolderDao
) {
    /**
     * Exposes a Flow of the full hierarchical folder tree built from [FolderDao.getAllFolders].
     * Root nodes are folders where [FolderEntity.parentId] is null.
     * Children are nested recursively and siblings are sorted ascending by name (case-insensitive).
     */
    fun getAllFoldersTreeFlow(): Flow<List<FolderTreeNode>> =
        folderDao.getAllFolders().map { folders ->
            buildFolderTree(folders)
        }

    fun getAllFolders(): Flow<List<FolderEntity>> = folderDao.getAllFolders()

    suspend fun getAllFoldersOnce(): List<FolderEntity> = folderDao.getAllFoldersOnce()

    fun getRootFolders(): Flow<List<FolderEntity>> = folderDao.getRootFolders()

    fun getChildFolders(parentId: Long): Flow<List<FolderEntity>> = folderDao.getChildFolders(parentId)

    suspend fun getFolderById(id: Long): FolderEntity? = folderDao.getFolderById(id)

    suspend fun insert(folder: FolderEntity): Long = folderDao.insert(folder)

    suspend fun update(folder: FolderEntity) = folderDao.update(folder)

    suspend fun delete(folder: FolderEntity) = folderDao.delete(folder)

    suspend fun rename(id: Long, name: String) = folderDao.rename(id, name)

    suspend fun deleteById(folderId: Long) = folderDao.deleteById(folderId)

    fun searchAllFolders(query: String): Flow<List<FolderEntity>> = folderDao.searchAllFolders(query)

    fun searchChildFolders(parentId: Long, query: String): Flow<List<FolderEntity>> =
        folderDao.searchChildFolders(parentId, query)

    companion object {
        /**
         * Builds a tree of [FolderTreeNode] from a flat list of [FolderEntity].
         * Root nodes are folders with parentId == null.
         * Children are nested recursively where child.parentId == parent.id.
         * Siblings are sorted ascending by name (case-insensitive).
         */
        fun buildFolderTree(folders: List<FolderEntity>): List<FolderTreeNode> {
            val foldersByParent = folders.groupBy { it.parentId }
            val visited = mutableSetOf<Long>()

            fun buildNodes(parentId: Long?): List<FolderTreeNode> {
                val siblings = foldersByParent[parentId] ?: emptyList()
                return siblings
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                    .mapNotNull { folder ->
                        if (visited.contains(folder.id)) {
                            null
                        } else {
                            visited.add(folder.id)
                            val children = buildNodes(folder.id)
                            FolderTreeNode(folder = folder, children = children)
                        }
                    }
            }

            return buildNodes(null)
        }
    }
}
