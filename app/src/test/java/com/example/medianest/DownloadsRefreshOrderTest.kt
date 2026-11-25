package com.example.medianest

import com.example.medianest.ui.viewmodel.LocalFileReconcile
import com.example.medianest.ui.viewmodel.RefreshPhase
import com.example.medianest.ui.viewmodel.reconcileLocalFile
import com.example.medianest.ui.viewmodel.refreshPhases
import com.example.medianest.ui.viewmodel.shouldMarkMissing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pull-to-refresh must report a file that is already gone on disk before it waits on the network,
 * and must never leave the spinner on after a failed sync.
 */
class DownloadsRefreshOrderTest {

    @Test
    fun refreshReconcilesLocalFilesBeforeSync() {
        assertEquals(
            listOf(RefreshPhase.LOCAL_RECONCILE, RefreshPhase.SYNC),
            refreshPhases,
        )
    }

    @Test
    fun completedDownloadWithMissingFileIsMarkedMissing() {
        assertTrue(shouldMarkMissing(statusIsCompleted = true, fileExists = false))
        assertEquals(
            LocalFileReconcile.MARK_MISSING,
            reconcileLocalFile(statusIsCompleted = true, fileExists = false),
        )
    }

    @Test
    fun completedDownloadWithFileOnDiskClearsMissingFlag() {
        assertFalse(shouldMarkMissing(statusIsCompleted = true, fileExists = true))
        assertEquals(
            LocalFileReconcile.CLEAR_MISSING,
            reconcileLocalFile(statusIsCompleted = true, fileExists = true),
        )
    }

    @Test
    fun unfinishedDownloadIsLeftUntouched() {
        assertFalse(shouldMarkMissing(statusIsCompleted = false, fileExists = false))
        assertEquals(
            LocalFileReconcile.NONE,
            reconcileLocalFile(statusIsCompleted = false, fileExists = false),
        )
        assertEquals(
            LocalFileReconcile.NONE,
            reconcileLocalFile(statusIsCompleted = false, fileExists = true),
        )
    }
}
