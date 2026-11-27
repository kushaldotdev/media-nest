package com.example.medianest.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class DownloadThumbnailCacheTest {

    @Test
    fun getCachedThumbnail_returnsNullForNullOrBlankUrls() {
        val cache = ConcurrentHashMap<String, String>()
        cache["https://example.com/thumb.jpg"] = "cached-bitmap"

        assertNull(cache.getCachedThumbnail(null))
        assertNull(cache.getCachedThumbnail(""))
        assertNull(cache.getCachedThumbnail("   "))
        assertNull(cache.getCachedThumbnail("\t\n"))
    }

    @Test
    fun getCachedThumbnail_returnsNullForMissingUrlAndValueForPresentUrl() {
        val cache = ConcurrentHashMap<String, String>()
        val url = "https://example.com/thumb.jpg"
        cache[url] = "cached-bitmap"

        assertNull(cache.getCachedThumbnail("https://example.com/other.jpg"))
        assertEquals("cached-bitmap", cache.getCachedThumbnail(url))
    }

    @Test
    fun putCachedThumbnail_ignoresNullBlankUrlAndNullValue() {
        val cache = ConcurrentHashMap<String, String>()

        cache.putCachedThumbnail(null, "bitmap")
        cache.putCachedThumbnail("", "bitmap")
        cache.putCachedThumbnail("   ", "bitmap")
        cache.putCachedThumbnail("https://example.com/thumb.jpg", null)

        assertTrue(cache.isEmpty())

        val validUrl = "https://example.com/valid.jpg"
        cache.putCachedThumbnail(validUrl, "valid-bitmap")
        assertEquals(1, cache.size)
        assertEquals("valid-bitmap", cache.getCachedThumbnail(validUrl))
    }
}
