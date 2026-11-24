package com.example.medianest

import com.example.medianest.data.local.entity.DownloadEntity
import com.example.medianest.data.local.entity.DownloadStatus
import com.example.medianest.data.model.StreamSource
import com.example.medianest.ui.viewmodel.PlaybackSource
import com.example.medianest.ui.viewmodel.resolvePlaybackSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A downloaded file must start playback without the network being awaited; the network may only
 * be awaited when nothing local is playable.
 */
class PlayerLocalFirstPlaybackTest {

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private fun download(
        id: Long,
        format: String = "video",
        quality: String = "720p",
        path: String,
    ): DownloadEntity = DownloadEntity(
        id = id,
        videoId = "vid",
        url = "https://example.com/$id",
        format = format,
        quality = quality,
        filePath = path,
        status = DownloadStatus.COMPLETED,
    )

    private fun existingFile(name: String): String =
        tempFolder.newFile(name).absolutePath

    private fun stream(quality: String, format: String = "video", codec: String = ""): StreamSource =
        StreamSource(
            url = "https://example.com/$quality",
            format = format,
            quality = quality,
            mimeType = "video/mp4",
            codec = codec,
            contentLength = 1000L,
        )

    @Test
    fun downloadedFileWithoutInfoResolvesLocal() {
        val local = download(id = 1, path = existingFile("a.mp4"))

        val source = resolvePlaybackSource(
            localDownloads = listOf(local),
            candidateStreamSource = null,
            hasInfo = false,
            downloadId = null,
        )

        assertEquals(PlaybackSource.Local(local), source)
    }

    @Test
    fun downloadedFileWinsOverAvailableInfo() {
        val local = download(id = 1, quality = "720p (avc)", path = existingFile("a.mp4"))

        val source = resolvePlaybackSource(
            localDownloads = listOf(local),
            candidateStreamSource = stream("720p"),
            hasInfo = true,
            downloadId = null,
        )

        assertEquals(PlaybackSource.Local(local), source)
    }

    @Test
    fun requestedDownloadIdResolvesThatFile() {
        val older = download(id = 1, quality = "1080p", path = existingFile("a.mp4"))
        val requested = download(id = 2, quality = "360p (avc)", path = existingFile("b.mp4"))

        val source = resolvePlaybackSource(
            localDownloads = listOf(older, requested),
            candidateStreamSource = null,
            hasInfo = true,
            downloadId = 2L,
        )

        assertEquals(PlaybackSource.Local(requested), source)
    }

    @Test
    fun noLocalFileWithInfoResolvesStream() {
        val candidate = stream("1080p")

        val source = resolvePlaybackSource(
            localDownloads = emptyList(),
            candidateStreamSource = candidate,
            hasInfo = true,
            downloadId = null,
        )

        assertEquals(PlaybackSource.Stream(candidate), source)
    }

    @Test
    fun noLocalFileWithoutInfoNeedsNetwork() {
        val source = resolvePlaybackSource(
            localDownloads = emptyList(),
            candidateStreamSource = null,
            hasInfo = false,
            downloadId = null,
        )

        assertEquals(PlaybackSource.NeedsNetwork, source)
    }

    @Test
    fun missingFileOnDiskDoesNotResolveLocal() {
        val gone = download(id = 1, path = tempFolder.root.resolve("deleted.mp4").absolutePath)

        val source = resolvePlaybackSource(
            localDownloads = listOf(gone),
            candidateStreamSource = null,
            hasInfo = false,
            downloadId = null,
        )

        assertEquals(PlaybackSource.NeedsNetwork, source)
    }

    @Test
    fun requestForDeletedDownloadIdFallsBackToNetwork() {
        val local = download(id = 1, quality = "720p", path = existingFile("a.mp4"))

        val source = resolvePlaybackSource(
            localDownloads = listOf(local),
            candidateStreamSource = null,
            hasInfo = false,
            downloadId = 99L,
        )

        assertEquals(PlaybackSource.NeedsNetwork, source)
    }

    @Test
    fun unmatchedQualityFallsBackToStream() {
        val local = download(id = 1, quality = "480p (avc)", path = existingFile("a.mp4"))
        val candidate = stream("1080p")

        val source = resolvePlaybackSource(
            localDownloads = listOf(local),
            candidateStreamSource = candidate,
            hasInfo = true,
            downloadId = null,
        )

        assertEquals(PlaybackSource.Stream(candidate), source)
    }

    @Test
    fun noInfoAndNoQualityMatchPlaysBestLocalFile() {
        val low = download(id = 1, quality = "360p (avc)", path = existingFile("low.mp4"))
        val high = download(id = 2, quality = "1080p (avc)", path = existingFile("high.mp4"))
        val audio = download(id = 3, format = "audio", quality = "128kbps", path = existingFile("audio.m4a"))

        val source = resolvePlaybackSource(
            localDownloads = listOf(low, audio, high),
            candidateStreamSource = null,
            hasInfo = false,
            downloadId = null,
        )

        assertTrue(source is PlaybackSource.Local)
        assertEquals(high.id, (source as PlaybackSource.Local).download.id)
    }
}
