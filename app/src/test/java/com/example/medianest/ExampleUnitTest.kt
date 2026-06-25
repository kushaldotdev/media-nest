package com.example.medianest

import com.example.medianest.extraction.DownloaderProvider
import com.example.medianest.extraction.YouTubeExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe

class ExampleUnitTest {
    @Test
    fun testAudioTracks() {
        runBlocking {
            // Initialize NewPipe with downloader
            NewPipe.init(DownloaderProvider.getDownloader())
            
            val extractor = YouTubeExtractor()
            val info = extractor.extractVideo("https://www.youtube.com/watch?v=0e3GPea1Tyg")
            
            println("Video Title: ${info.title}")
            val audioStreams = info.streamSources.filter { it.format == "audio" }
            println("Extracted ${audioStreams.size} audio streams after filtering:")
            audioStreams.forEach { stream ->
                println("Audio - Quality: ${stream.quality}, Codec: ${stream.codec}, MimeType: ${stream.mimeType}, Language: ${stream.language}")
            }
        }
    }
}