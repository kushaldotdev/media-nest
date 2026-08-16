package com.example.medianest

import com.example.medianest.data.local.entity.DownloadEntity
import com.example.medianest.data.local.entity.DownloadStatus
import com.example.medianest.data.preferences.DownloadPreferences
import com.example.medianest.ui.screens.DownloadSortCategory
import com.example.medianest.ui.screens.applyQueueOrder
import com.example.medianest.ui.screens.getSortCategory
import com.example.medianest.ui.screens.isSortAscending
import com.example.medianest.ui.screens.resolveDownloadResolution
import com.example.medianest.ui.screens.sortDownloads
import com.example.medianest.ui.screens.toggleSortMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadsSortingTest {

    private fun createDownload(
        id: Long,
        title: String = "Video $id",
        downloadedAt: Long = id * 1000L,
        progress: Float = id * 0.1f,
        fileSizeBytes: Long = id * 1000000L,
        status: DownloadStatus = DownloadStatus.COMPLETED,
        quality: String = "720p"
    ): DownloadEntity {
        return DownloadEntity(
            id = id,
            videoId = "vid_$id",
            url = "https://example.com/stream_$id",
            videoUrl = "https://youtube.com/watch?v=vid_$id",
            format = "video",
            quality = quality,
            status = status,
            progress = progress,
            fileSizeBytes = fileSizeBytes,
            downloadedAt = downloadedAt,
            downloadUuid = "uuid_$id",
            outputRoot = "/storage/downloads"
        )
    }

    @Test
    fun testGetSortCategoryAndAscending() {
        assertEquals(DownloadSortCategory.DATE, getSortCategory("DATE_DESC"))
        assertFalse(isSortAscending("DATE_DESC"))

        assertEquals(DownloadSortCategory.DATE, getSortCategory("DATE_ASC"))
        assertTrue(isSortAscending("DATE_ASC"))

        assertEquals(DownloadSortCategory.PROGRESS, getSortCategory("PROGRESS_DESC"))
        assertFalse(isSortAscending("PROGRESS_DESC"))

        assertEquals(DownloadSortCategory.SIZE, getSortCategory("SIZE_DESC"))
        assertEquals(DownloadSortCategory.STATUS, getSortCategory("STATUS_ASC"))
        assertTrue(isSortAscending("STATUS_ASC"))
    }

    @Test
    fun testToggleSortMode() {
        // Toggling active category flips direction
        assertEquals("DATE_ASC", toggleSortMode(DownloadSortCategory.DATE, "DATE_DESC"))
        assertEquals("DATE_DESC", toggleSortMode(DownloadSortCategory.DATE, "DATE_ASC"))
        assertEquals("PROGRESS_ASC", toggleSortMode(DownloadSortCategory.PROGRESS, "PROGRESS_DESC"))
        assertEquals("STATUS_DESC", toggleSortMode(DownloadSortCategory.STATUS, "STATUS_ASC"))

        // Selecting inactive category resets to default mode
        assertEquals("PROGRESS_DESC", toggleSortMode(DownloadSortCategory.PROGRESS, "DATE_DESC"))
        assertEquals("SIZE_DESC", toggleSortMode(DownloadSortCategory.SIZE, "DATE_ASC"))
        assertEquals("STATUS_ASC", toggleSortMode(DownloadSortCategory.STATUS, "SIZE_DESC"))
        assertEquals("DATE_DESC", toggleSortMode(DownloadSortCategory.DATE, "STATUS_ASC"))
    }

    @Test
    fun testSortDownloadsByDate() {
        val d1 = createDownload(id = 1, downloadedAt = 1000L)
        val d2 = createDownload(id = 2, downloadedAt = 5000L)
        val d3 = createDownload(id = 3, downloadedAt = 3000L)
        val list = listOf(d1, d2, d3)

        val desc = sortDownloads(list, "DATE_DESC")
        assertEquals(listOf(2L, 3L, 1L), desc.map { it.id })

        val asc = sortDownloads(list, "DATE_ASC")
        assertEquals(listOf(1L, 3L, 2L), asc.map { it.id })
    }

    @Test
    fun testSortDownloadsByProgress() {
        val d1 = createDownload(id = 1, progress = 0.2f)
        val d2 = createDownload(id = 2, progress = 0.9f)
        val d3 = createDownload(id = 3, progress = 0.5f)
        val list = listOf(d1, d2, d3)

        val desc = sortDownloads(list, "PROGRESS_DESC")
        assertEquals(listOf(2L, 3L, 1L), desc.map { it.id })

        val asc = sortDownloads(list, "PROGRESS_ASC")
        assertEquals(listOf(1L, 3L, 2L), asc.map { it.id })
    }

    @Test
    fun testSortDownloadsBySize() {
        val d1 = createDownload(id = 1, fileSizeBytes = 1000L)
        val d2 = createDownload(id = 2, fileSizeBytes = 5000L)
        val d3 = createDownload(id = 3, fileSizeBytes = 2000L)
        val list = listOf(d1, d2, d3)

        val desc = sortDownloads(list, "SIZE_DESC")
        assertEquals(listOf(2L, 3L, 1L), desc.map { it.id })

        val asc = sortDownloads(list, "SIZE_ASC")
        assertEquals(listOf(1L, 3L, 2L), asc.map { it.id })
    }

    @Test
    fun testSortDownloadsByStatus() {
        val dComp = createDownload(id = 1, status = DownloadStatus.COMPLETED)
        val dQueued = createDownload(id = 2, status = DownloadStatus.QUEUED)
        val dDl = createDownload(id = 3, status = DownloadStatus.DOWNLOADING)
        val dPaused = createDownload(id = 4, status = DownloadStatus.PAUSED)
        val list = listOf(dComp, dQueued, dDl, dPaused)

        // STATUS_ASC: DOWNLOADING (0), QUEUED (1), PAUSED (2), COMPLETED (5)
        val asc = sortDownloads(list, "STATUS_ASC")
        assertEquals(listOf(3L, 2L, 4L, 1L), asc.map { it.id })

        // STATUS_DESC: COMPLETED (5), PAUSED (2), QUEUED (1), DOWNLOADING (0)
        val desc = sortDownloads(list, "STATUS_DESC")
        assertEquals(listOf(1L, 4L, 2L, 3L), desc.map { it.id })
    }

    @Test
    fun testApplyQueueOrder() {
        val d1 = createDownload(id = 1, downloadedAt = 1000L)
        val d2 = createDownload(id = 2, downloadedAt = 2000L)
        val d3 = createDownload(id = 3, downloadedAt = 3000L)
        val list = listOf(d1, d2, d3)

        // When queue order is empty, fallback to sortMode
        val naturalSort = applyQueueOrder(list, emptyList(), "DATE_DESC")
        assertEquals(listOf(3L, 2L, 1L), naturalSort.map { it.id })

        // When custom in-memory queue order is present
        val customOrder = listOf(1L, 3L, 2L)
        val ordered = applyQueueOrder(list, customOrder, "DATE_DESC")
        assertEquals(listOf(1L, 3L, 2L), ordered.map { it.id })

        // When a new download exists not yet in custom order, it appends sorted
        val d4 = createDownload(id = 4, downloadedAt = 4000L)
        val withNew = applyQueueOrder(listOf(d1, d2, d3, d4), customOrder, "DATE_DESC")
        assertEquals(listOf(1L, 3L, 2L, 4L), withNew.map { it.id })
    }

    @Test
    fun testResolveDownloadResolution() {
        assertEquals("1080p", resolveDownloadResolution("1080p", "720p"))
        assertEquals("720p", resolveDownloadResolution("", "720p"))
        assertEquals("720p", resolveDownloadResolution(null, "720p"))
        assertEquals(DownloadPreferences.DEFAULT_RESOLUTION, resolveDownloadResolution(null, null))
        assertEquals("360p", DownloadPreferences.DEFAULT_RESOLUTION)
    }
}
