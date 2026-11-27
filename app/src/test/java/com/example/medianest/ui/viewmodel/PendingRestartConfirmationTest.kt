package com.example.medianest.ui.viewmodel

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PendingRestartConfirmationTest {

    @Before
    fun setUp() {
        PendingRestartConfirmation.clearPendingRestart()
        PendingRestartConfirmation.consumeNavigateToDownloads()
        PendingRestartConfirmation.consumeNavigateToUpdates()
    }

    @After
    fun tearDown() {
        PendingRestartConfirmation.clearPendingRestart()
        PendingRestartConfirmation.consumeNavigateToDownloads()
        PendingRestartConfirmation.consumeNavigateToUpdates()
    }

    @Test
    fun requestRestart_isStickyForLateCollector() = runBlocking {
        // Simulates MainActivity receiving ACTION_CONFIRM_RESTART before DownloadsScreen mounts.
        PendingRestartConfirmation.requestRestart(42L)

        // Later, DownloadsScreen mounts and subscribes to pendingRestart.
        val lateObserved = PendingRestartConfirmation.pendingRestart.first()

        assertNotNull(lateObserved)
        assertEquals(42L, lateObserved?.downloadId)
    }

    @Test
    fun requestRestart_sameIdAfterConsume_triggersAgain() = runBlocking {
        PendingRestartConfirmation.requestRestart(42L)
        val first = PendingRestartConfirmation.pendingRestart.value
        assertNotNull(first)

        PendingRestartConfirmation.consumePendingRestart(first!!)
        assertNull(PendingRestartConfirmation.pendingRestart.value)

        PendingRestartConfirmation.requestRestart(42L)
        val second = PendingRestartConfirmation.pendingRestart.first()

        assertNotNull(second)
        assertEquals(42L, second?.downloadId)
        assertNotEquals(first, second)
    }

    @Test
    fun requestRestart_sameIdBeforeConsume_emitsDistinctRequest() {
        PendingRestartConfirmation.requestRestart(42L)
        val first = PendingRestartConfirmation.pendingRestart.value

        PendingRestartConfirmation.requestRestart(42L)
        val second = PendingRestartConfirmation.pendingRestart.value

        assertNotNull(first)
        assertNotNull(second)
        assertEquals(42L, second?.downloadId)
        assertNotEquals(first, second)
    }

    @Test
    fun consumePendingRestart_clearsMatchingRequestOnly() {
        PendingRestartConfirmation.requestRestart(10L)
        val staleRequest = PendingRestartConfirmation.pendingRestart.value!!

        PendingRestartConfirmation.requestRestart(20L)
        val latestRequest = PendingRestartConfirmation.pendingRestart.value!!

        PendingRestartConfirmation.consumePendingRestart(staleRequest)
        assertEquals(latestRequest, PendingRestartConfirmation.pendingRestart.value)

        PendingRestartConfirmation.consumePendingRestart(latestRequest)
        assertNull(PendingRestartConfirmation.pendingRestart.value)
    }

    @Test
    fun consumePendingRestartById_clearsOnlyMatchingId() {
        PendingRestartConfirmation.requestRestart(77L)

        PendingRestartConfirmation.consumePendingRestart(99L)
        assertEquals(77L, PendingRestartConfirmation.pendingRestart.value?.downloadId)

        PendingRestartConfirmation.consumePendingRestart(77L)
        assertNull(PendingRestartConfirmation.pendingRestart.value)
    }

    @Test
    fun navigationFlags_areStickyUntilConsumed() = runBlocking {
        PendingRestartConfirmation.requestNavigateToDownloads()
        assertTrue(PendingRestartConfirmation.navigateToDownloads.first())
        PendingRestartConfirmation.consumeNavigateToDownloads()
        assertFalse(PendingRestartConfirmation.navigateToDownloads.value)

        PendingRestartConfirmation.requestNavigateToUpdates()
        assertTrue(PendingRestartConfirmation.navigateToUpdates.first())
        PendingRestartConfirmation.consumeNavigateToUpdates()
        assertFalse(PendingRestartConfirmation.navigateToUpdates.value)
    }
}
