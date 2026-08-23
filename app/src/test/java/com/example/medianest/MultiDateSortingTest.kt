package com.example.medianest

import com.example.medianest.data.local.entity.VideoEntity
import com.example.medianest.ui.components.MediaNestSortOption
import com.example.medianest.ui.viewmodel.SortCategory
import com.example.medianest.ui.utils.UiUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiDateSortingTest {

    @Test
    fun testMediaNestSortOptionsDefinitions() {
        assertEquals("DATE_PUBLISHED", MediaNestSortOption.DATE_PUBLISHED.id)
        assertEquals("Published Date", MediaNestSortOption.DATE_PUBLISHED.label)
        assertEquals(R.drawable.ic_mn_history, MediaNestSortOption.DATE_PUBLISHED.iconRes)

        assertEquals("LAST_WATCHED", MediaNestSortOption.LAST_WATCHED.id)
        assertEquals("Last Watched", MediaNestSortOption.LAST_WATCHED.label)
        assertEquals(R.drawable.ic_mn_watched, MediaNestSortOption.LAST_WATCHED.iconRes)

        assertEquals("DATE_ADDED", MediaNestSortOption.DATE_ADDED.id)
        assertEquals("Date Added", MediaNestSortOption.DATE_ADDED.label)
        assertEquals(R.drawable.ic_mn_folder, MediaNestSortOption.DATE_ADDED.iconRes)

        assertEquals(MediaNestSortOption.DATE_ADDED, MediaNestSortOption.DATE)
    }

    @Test
    fun testCuratedOptionsLists() {
        val collections = MediaNestSortOption.CollectionsMediaOptions
        assertEquals(6, collections.size)
        assertEquals(listOf(
            MediaNestSortOption.DATE_ADDED,
            MediaNestSortOption.DATE_PUBLISHED,
            MediaNestSortOption.LAST_WATCHED,
            MediaNestSortOption.TITLE,
            MediaNestSortOption.DURATION,
            MediaNestSortOption.SIZE
        ), collections)

        val subscriptions = MediaNestSortOption.SubscriptionMediaOptions
        assertEquals(5, subscriptions.size)
        assertEquals(listOf(
            MediaNestSortOption.DATE_PUBLISHED,
            MediaNestSortOption.DATE_ADDED,
            MediaNestSortOption.LAST_WATCHED,
            MediaNestSortOption.TITLE,
            MediaNestSortOption.DURATION
        ), subscriptions)

        val history = MediaNestSortOption.HistoryMediaOptions
        assertEquals(5, history.size)
        assertEquals(listOf(
            MediaNestSortOption.LAST_WATCHED,
            MediaNestSortOption.DATE_ADDED,
            MediaNestSortOption.DATE_PUBLISHED,
            MediaNestSortOption.TITLE,
            MediaNestSortOption.DURATION
        ), history)
    }

    @Test
    fun testSortCategoryEnumLabels() {
        assertEquals("Published Date", SortCategory.DATE_PUBLISHED.label)
        assertEquals("Last Watched", SortCategory.LAST_WATCHED.label)
        assertEquals("Date Added", SortCategory.DATE_ADDED.label)
        assertEquals("Name", SortCategory.NAME.label)
        assertEquals("Duration", SortCategory.DURATION.label)
        assertEquals("Size", SortCategory.SIZE.label)
    }

    @Test
    fun testMultiDateSortingComparatorsWithNullsAndOtherCategories() {
        val videoWithNulls = VideoEntity(
            id = "null_dates",
            title = "A Video",
            channelName = "Channel A",
            durationSeconds = 50L,
            uploadDate = null,
            addedAt = 500L,
            lastPlayedAt = null
        )
        val videoWithData = VideoEntity(
            id = "valid_dates",
            title = "Z Video",
            channelName = "Channel Z",
            durationSeconds = 500L,
            uploadDate = "2024-05-01",
            addedAt = 1500L,
            lastPlayedAt = 2500L
        )
        val list = listOf(videoWithNulls, videoWithData)

        // DATE_PUBLISHED with null fallback: videoWithData > videoWithNulls (0L)
        val byPublished = list.sortedWith(compareBy<VideoEntity> { UiUtils.parseUploadDate(it.uploadDate)?.time ?: 0L }.reversed())
        assertEquals("valid_dates", byPublished[0].id)
        assertEquals("null_dates", byPublished[1].id)

        // LAST_WATCHED with null fallback: videoWithData (2500L) > videoWithNulls (0L)
        val byWatched = list.sortedWith(compareBy<VideoEntity> { it.lastPlayedAt ?: 0L }.reversed())
        assertEquals("valid_dates", byWatched[0].id)
        assertEquals("null_dates", byWatched[1].id)

        // NAME: "A Video" < "Z Video"
        val byNameAsc = list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        assertEquals("null_dates", byNameAsc[0].id)
        assertEquals("valid_dates", byNameAsc[1].id)

        // DURATION: 500L > 50L
        val byDurationDesc = list.sortedWith(compareBy<VideoEntity> { it.durationSeconds }.reversed())
        assertEquals("valid_dates", byDurationDesc[0].id)
        assertEquals("null_dates", byDurationDesc[1].id)
    }

    @Test
    fun testNullsLastComparatorBothAscAndDesc() {
        val videoWithNulls = VideoEntity(
            id = "null_dates",
            title = "A Video",
            channelName = "Channel A",
            durationSeconds = 50L,
            uploadDate = null,
            addedAt = 500L,
            lastPlayedAt = null
        )
        val videoOld = VideoEntity(
            id = "old_date",
            title = "Old Video",
            channelName = "Channel B",
            durationSeconds = 100L,
            uploadDate = "2022-01-01",
            addedAt = 1000L,
            lastPlayedAt = 1000L
        )
        val videoNew = VideoEntity(
            id = "new_date",
            title = "New Video",
            channelName = "Channel C",
            durationSeconds = 200L,
            uploadDate = "2024-05-01",
            addedAt = 2000L,
            lastPlayedAt = 2000L
        )
        val list = listOf(videoWithNulls, videoOld, videoNew)

        // Published Date ASC: Old -> New -> Null
        val publishedAsc = list.sortedWith(UiUtils.nullsLastComparator(ascending = true) { UiUtils.parseUploadDate(it.uploadDate)?.time })
        assertEquals("old_date", publishedAsc[0].id)
        assertEquals("new_date", publishedAsc[1].id)
        assertEquals("null_dates", publishedAsc[2].id)

        // Published Date DESC: New -> Old -> Null (Null must ALWAYS be at the bottom)
        val publishedDesc = list.sortedWith(UiUtils.nullsLastComparator(ascending = false) { UiUtils.parseUploadDate(it.uploadDate)?.time })
        assertEquals("new_date", publishedDesc[0].id)
        assertEquals("old_date", publishedDesc[1].id)
        assertEquals("null_dates", publishedDesc[2].id)

        // Last Watched ASC: Old -> New -> Null
        val watchedAsc = list.sortedWith(UiUtils.nullsLastComparator(ascending = true) { it.lastPlayedAt })
        assertEquals("old_date", watchedAsc[0].id)
        assertEquals("new_date", watchedAsc[1].id)
        assertEquals("null_dates", watchedAsc[2].id)

        // Last Watched DESC: New -> Old -> Null (Null must ALWAYS be at the bottom)
        val watchedDesc = list.sortedWith(UiUtils.nullsLastComparator(ascending = false) { it.lastPlayedAt })
        assertEquals("new_date", watchedDesc[0].id)
        assertEquals("old_date", watchedDesc[1].id)
        assertEquals("null_dates", watchedDesc[2].id)
    }

    @Test
    fun testLegacyDateStringResolution() {
        fun mapSortCategory(sortBy: String): SortCategory = when (sortBy.uppercase()) {
            "DATE_PUBLISHED" -> SortCategory.DATE_PUBLISHED
            "LAST_WATCHED" -> SortCategory.LAST_WATCHED
            "DATE_ADDED", "DATE" -> SortCategory.DATE_ADDED
            "TITLE", "NAME" -> SortCategory.NAME
            "DURATION" -> SortCategory.DURATION
            "SIZE" -> SortCategory.SIZE
            else -> SortCategory.DATE_PUBLISHED
        }

        assertEquals(SortCategory.DATE_ADDED, mapSortCategory("DATE"))
        assertEquals(SortCategory.DATE_ADDED, mapSortCategory("date"))
        assertEquals(SortCategory.DATE_ADDED, mapSortCategory("DATE_ADDED"))
        assertEquals(SortCategory.DATE_PUBLISHED, mapSortCategory("DATE_PUBLISHED"))
        assertEquals(SortCategory.LAST_WATCHED, mapSortCategory("LAST_WATCHED"))
    }
}
