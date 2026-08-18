package com.example.medianest.data.repository

import com.example.medianest.data.local.entity.FolderEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderRepositoryTest {

    private fun createFolder(id: Long, name: String, parentId: Long? = null): FolderEntity {
        return FolderEntity(
            id = id,
            name = name,
            parentId = parentId,
            createdAt = 1000L,
            updatedAt = 1000L
        )
    }

    @Test
    fun testBuildFolderTreeEmpty() {
        val tree = FolderRepository.buildFolderTree(emptyList())
        assertTrue(tree.isEmpty())
    }

    @Test
    fun testBuildFolderTreeFlatRootsSorted() {
        val folders = listOf(
            createFolder(1, "Zebra"),
            createFolder(2, "Apple"),
            createFolder(3, "mango")
        )
        val tree = FolderRepository.buildFolderTree(folders)

        assertEquals(3, tree.size)
        assertEquals("Apple", tree[0].folder.name)
        assertEquals("mango", tree[1].folder.name)
        assertEquals("Zebra", tree[2].folder.name)
        assertTrue(tree[0].children.isEmpty())
        assertTrue(tree[1].children.isEmpty())
        assertTrue(tree[2].children.isEmpty())
    }

    @Test
    fun testBuildFolderTreeNestedHierarchy() {
        val folders = listOf(
            createFolder(id = 1, name = "Music", parentId = null),
            createFolder(id = 2, name = "Rock", parentId = 1),
            createFolder(id = 3, name = "90s Rock", parentId = 2),
            createFolder(id = 4, name = "80s Rock", parentId = 2),
            createFolder(id = 5, name = "Pop", parentId = 1),
            createFolder(id = 6, name = "Videos", parentId = null),
            createFolder(id = 7, name = "Tutorials", parentId = 6)
        )

        val tree = FolderRepository.buildFolderTree(folders)

        // Roots should be "Music" and "Videos" sorted alphabetically
        assertEquals(2, tree.size)
        assertEquals("Music", tree[0].folder.name)
        assertEquals("Videos", tree[1].folder.name)

        // Music children: "Pop", "Rock"
        val musicChildren = tree[0].children
        assertEquals(2, musicChildren.size)
        assertEquals("Pop", musicChildren[0].folder.name)
        assertEquals("Rock", musicChildren[1].folder.name)

        // Pop has no children
        assertTrue(musicChildren[0].children.isEmpty())

        // Rock children: "80s Rock", "90s Rock"
        val rockChildren = musicChildren[1].children
        assertEquals(2, rockChildren.size)
        assertEquals("80s Rock", rockChildren[0].folder.name)
        assertEquals("90s Rock", rockChildren[1].folder.name)

        // Videos children: "Tutorials"
        val videoChildren = tree[1].children
        assertEquals(1, videoChildren.size)
        assertEquals("Tutorials", videoChildren[0].folder.name)
    }

    @Test
    fun testFlattenWithDepth() {
        val folders = listOf(
            createFolder(id = 1, name = "Music", parentId = null),
            createFolder(id = 2, name = "Rock", parentId = 1),
            createFolder(id = 3, name = "90s", parentId = 2),
            createFolder(id = 4, name = "Pop", parentId = 1),
            createFolder(id = 5, name = "Archive", parentId = null)
        )

        val tree = FolderRepository.buildFolderTree(folders)
        val flattened = tree.flattenWithDepth()

        val expected = listOf(
            "Archive" to 0,
            "Music" to 0,
            "Pop" to 1,
            "Rock" to 1,
            "90s" to 2
        )

        val actual = flattened.map { it.first.name to it.second }
        assertEquals(expected, actual)
    }

    @Test
    fun testCycleProtection() {
        // Corrupted hierarchy with cyclic parent pointers: 1 -> 2 -> 1
        val folders = listOf(
            createFolder(id = 1, name = "Root", parentId = null),
            createFolder(id = 2, name = "Child", parentId = 1),
            createFolder(id = 3, name = "CycleNode", parentId = 2)
        )
        // If node 1 somehow was also a child of 3:
        val cycleFolders = folders + createFolder(id = 1, name = "Root", parentId = 3)
        val tree = FolderRepository.buildFolderTree(cycleFolders)
        // Should not throw StackOverflowError and should terminate safely
        val flattened = tree.flattenWithDepth()
        assertTrue(flattened.isNotEmpty())
    }
}
