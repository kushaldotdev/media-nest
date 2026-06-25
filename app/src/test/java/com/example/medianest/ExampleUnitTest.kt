package com.example.medianest

import com.example.medianest.extraction.DownloaderProvider
import com.example.medianest.extraction.YouTubeExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe

class ExampleUnitTest {
    @Test
    fun testAudioBitrates() = runBlocking {
        // Initialize NewPipe with downloader
        NewPipe.init(DownloaderProvider.getDownloader())
        
        val extractor = YouTubeExtractor()
        // Using a standard public video (Rick Astley - Never Gonna Give You Up)
        val info = extractor.extractVideo("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        
        println("Video Title: ${info.title}")
        val audioStreams = info.streamSources.filter { it.format == "audio" }
        println("Found ${audioStreams.size} audio streams:")
        audioStreams.forEach { stream ->
            println("Audio - Quality: ${stream.quality}, Codec: ${stream.codec}, MimeType: ${stream.mimeType}")
        }
    }
}