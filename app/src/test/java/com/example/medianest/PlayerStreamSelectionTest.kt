package com.example.medianest

import com.example.medianest.data.model.StreamSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PlayerStreamSelectionTest {

    private fun selectBestDefaultStream(streams: List<StreamSource>): Pair<Int, StreamSource> {
        val idx360 = streams.indexOfFirst { it.format != "audio" && it.quality == "360p" }
        if (idx360 >= 0) return idx360 to streams[idx360]
        val idxVideo = streams.indexOfFirst { it.format != "audio" && it.format != "audio_only" }
        if (idxVideo >= 0) return idxVideo to streams[idxVideo]
        if (streams.isNotEmpty()) return 0 to streams[0]
        throw IllegalStateException("No streams available")
    }

    @Test
    fun testSelectBestDefaultStreamPrefers360p() {
        val streams = listOf(
            StreamSource(url = "url_audio", format = "audio", quality = "128kbps", mimeType = "audio/mp4", contentLength = 1000L),
            StreamSource(url = "url_720", format = "video", quality = "720p", mimeType = "video/mp4", contentLength = 5000L),
            StreamSource(url = "url_360", format = "video", quality = "360p", mimeType = "video/mp4", contentLength = 3000L)
        )
        val (index, selected) = selectBestDefaultStream(streams)
        assertEquals(2, index)
        assertEquals("360p", selected.quality)
    }

    @Test
    fun testSelectBestDefaultStreamFallsBackToNonAudioVideo() {
        val streams = listOf(
            StreamSource(url = "url_audio", format = "audio", quality = "128kbps", mimeType = "audio/mp4", contentLength = 1000L),
            StreamSource(url = "url_1080", format = "video", quality = "1080p", mimeType = "video/mp4", contentLength = 8000L)
        )
        val (index, selected) = selectBestDefaultStream(streams)
        assertEquals(1, index)
        assertEquals("1080p", selected.quality)
    }

    @Test
    fun testSelectBestDefaultStreamFallsBackToFirstIfAudioOnly() {
        val streams = listOf(
            StreamSource(url = "url_audio1", format = "audio", quality = "128kbps", mimeType = "audio/mp4", contentLength = 1000L),
            StreamSource(url = "url_audio2", format = "audio", quality = "256kbps", mimeType = "audio/mp4", contentLength = 2000L)
        )
        val (index, selected) = selectBestDefaultStream(streams)
        assertEquals(0, index)
        assertEquals("url_audio1", selected.url)
    }

    @Test
    fun testSelectBestDefaultStreamThrowsOnEmpty() {
        assertThrows(IllegalStateException::class.java) {
            selectBestDefaultStream(emptyList())
        }
    }
}
