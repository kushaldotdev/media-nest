package com.example.medianest.data.local.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Sibling folder names must be unique per parent group (null parentId = root group).
 * Rename must not count the folder itself as a conflict.
 */
class FolderNameConflictTest {

    private fun folder(id: Long, name: String, parentId: Long? = null): FolderEntity =
        FolderEntity(id = id, name = name, parentId = parentId, createdAt = 1000L, updatedAt = 1000L)

    private val siblings = listOf(
        folder(1, "Rock", parentId = null),
        folder(2, "Pop", parentId = null),
        folder(3, "Rock", parentId = 1)
    )

    @Test
    fun createRejectsExactDuplicateRootSibling() {
        val conflict = findFolderNameConflict(siblings, "Rock", parentId = null)
        assertEquals(1L, conflict?.id)
    }

    @Test
    fun createRejectsDuplicateIgnoringCase() {
        val conflict = findFolderNameConflict(siblings, "rock", parentId = null)
        assertEquals(1L, conflict?.id)
    }

    @Test
    fun createRejectsDuplicateAfterTrimmingInput() {
        val conflict = findFolderNameConflict(siblings, "  ROCK \n", parentId = null)
        assertEquals(1L, conflict?.id)
    }

    @Test
    fun createRejectsDuplicateWhenStoredNameHasWhitespace() {
        val folders = siblings + folder(4, " Archive ", parentId = null)
        val conflict = findFolderNameConflict(folders, "archive", parentId = null)
        assertEquals(4L, conflict?.id)
    }

    @Test
    fun createAllowsFreeNameInSameGroup() {
        assertNull(findFolderNameConflict(siblings, "Jazz", parentId = null))
    }

    @Test
    fun createAllowsSameNameUnderDifferentParent() {
        // "Rock" exists at the root and under folder 1; a third group may reuse it.
        assertNull(findFolderNameConflict(siblings, "Rock", parentId = 2))
    }

    @Test
    fun createTreatsNullParentAsItsOwnGroup() {
        // "Rock" under parent 1 does not block a root folder named "Rock".
        val childOnly = listOf(folder(3, "Rock", parentId = 1))
        assertNull(findFolderNameConflict(childOnly, "Rock", parentId = null))
        assertEquals(3L, findFolderNameConflict(childOnly, "Rock", parentId = 1)?.id)
    }

    @Test
    fun renameAllowsFolderToKeepItsOwnName() {
        assertNull(findFolderNameConflict(siblings, "Rock", parentId = null, excludingId = 1L))
    }

    @Test
    fun renameAllowsCaseAndWhitespaceVariantOfItsOwnName() {
        assertNull(findFolderNameConflict(siblings, " rock ", parentId = null, excludingId = 1L))
    }

    @Test
    fun renameRejectsSiblingNameIgnoringCase() {
        // Renaming folder 2 ("Pop") to "rock" collides with sibling folder 1 ("Rock").
        val conflict = findFolderNameConflict(siblings, "rock", parentId = null, excludingId = 2L)
        assertEquals(1L, conflict?.id)
    }

    @Test
    fun renameRejectsSiblingNameAfterTrimming() {
        val conflict = findFolderNameConflict(siblings, "  Rock  ", parentId = null, excludingId = 2L)
        assertEquals(1L, conflict?.id)
    }

    @Test
    fun renameAllowsNameFreeAmongSiblings() {
        assertNull(findFolderNameConflict(siblings, "Jazz", parentId = null, excludingId = 2L))
    }
}
