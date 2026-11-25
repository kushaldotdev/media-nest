package com.example.medianest

import com.example.medianest.ui.viewmodel.VideoDetailLoadMode
import com.example.medianest.ui.viewmodel.videoDetailLoadMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A video detail screen whose row is already on disk must show it without awaiting the online
 * extraction; only a reachable network may start that fetch.
 */
class VideoDetailOfflineFirstTest {

    @Test
    fun offlineLoadNeverFetchesOnline() {
        assertEquals(
            VideoDetailLoadMode.LocalOnly,
            videoDetailLoadMode(isNetworkAvailable = false, forceRefresh = false)
        )
    }

    @Test
    fun offlineForcedRefreshNeverFetchesOnline() {
        // Pull-to-refresh without a network cannot conjure streams; the local row stays visible.
        assertEquals(
            VideoDetailLoadMode.LocalOnly,
            videoDetailLoadMode(isNetworkAvailable = false, forceRefresh = true)
        )
    }

    @Test
    fun onlineLoadShowsLocalRowFirstThenFetchesOnline() {
        assertEquals(
            VideoDetailLoadMode.Online,
            videoDetailLoadMode(isNetworkAvailable = true, forceRefresh = false)
        )
    }

    @Test
    fun onlineForcedRefreshDoesNotPublishStaleLocalData() {
        assertEquals(
            VideoDetailLoadMode.OnlineRefresh,
            videoDetailLoadMode(isNetworkAvailable = true, forceRefresh = true)
        )
    }

    @Test
    fun networkAvailabilityAloneDecidesWhetherTheExtractionRuns() {
        for (forceRefresh in listOf(true, false)) {
            assertEquals(
                "forceRefresh=$forceRefresh",
                VideoDetailLoadMode.LocalOnly,
                videoDetailLoadMode(isNetworkAvailable = false, forceRefresh = forceRefresh)
            )
        }
    }
}
