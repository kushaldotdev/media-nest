package com.example.medianest

import com.example.medianest.ui.utils.UiUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class UiUtilsTest {

    @Test
    fun testParseUploadDate_isoFormat() {
        val date = UiUtils.parseUploadDate("2024-05-10T14:30:00Z")
        assertNotNull(date)

        val dateOffset = UiUtils.parseUploadDate("2024-05-10T14:30:00+02:00")
        assertNotNull(dateOffset)
    }

    @Test
    fun testParseUploadDate_prefixes() {
        val streamed = UiUtils.parseUploadDate("Streamed live on May 10, 2023")
        assertNotNull(streamed)

        val premiered = UiUtils.parseUploadDate("Premiered Jan 5, 2022")
        assertNotNull(premiered)

        val published = UiUtils.parseUploadDate("Published on Oct 14, 2021")
        assertNotNull(published)
    }

    @Test
    fun testParseUploadDate_standardDate() {
        val date = UiUtils.parseUploadDate("2024-05-10")
        assertNotNull(date)
    }

    @Test
    fun testParseUploadDate_relativeAgo() {
        val date9m = UiUtils.parseUploadDate("9 months ago")
        assertNotNull(date9m)
        val diffDays = (System.currentTimeMillis() - date9m!!.time) / (1000L * 60 * 60 * 24)
        assertTrue(diffDays in 260..280)

        val date2w = UiUtils.parseUploadDate("2 weeks ago")
        assertNotNull(date2w)
        val diffDays2w = (System.currentTimeMillis() - date2w!!.time) / (1000L * 60 * 60 * 24)
        assertTrue(diffDays2w in 13..15)
    }

    @Test
    fun testFormatReleaseDate_relativeBreakdown() {
        val dateIso = "2024-01-01T00:00:00Z"
        val formattedIso = UiUtils.formatReleaseDate(dateIso)
        assertNotNull(formattedIso)
        // Should contain unit breakdown like y, mo, w, d, h, m, s
        assertTrue(formattedIso!!.matches(Regex(""".*\d+[ymo|w|d|h|m|s].*""")))

        val formattedAgo = UiUtils.formatReleaseDate("9 months ago")
        assertNotNull(formattedAgo)
        assertTrue(formattedAgo!!.contains("9mo"))
    }

    @Test
    fun testFormatDuration() {
        assertEquals("1h 6m 5s", UiUtils.formatDuration(3965))
        assertEquals("4m 26s", UiUtils.formatDuration(266))
        assertEquals("58m 2s", UiUtils.formatDuration(3482))
    }
}
