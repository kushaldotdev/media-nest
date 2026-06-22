package com.example.medianest.extraction

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Response
import java.net.HttpURLConnection
import java.net.URL

object DownloaderProvider {
    fun getDownloader(): Downloader = object : Downloader() {
        override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): Response {
            val connection = URL(request.url()).openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 30000
            request.headers()?.forEach { (k, v) -> connection.setRequestProperty(k, v.joinToString()) }
            connection.connect()

            val responseCode = connection.responseCode
            val body = if (responseCode in 200..299) {
                connection.inputStream?.readBytes()?.toString(Charsets.UTF_8) ?: ""
            } else {
                connection.errorStream?.readBytes()?.toString(Charsets.UTF_8) ?: ""
            }
            return Response(
                responseCode,
                connection.responseMessage ?: "",
                connection.headerFields
                    ?.mapKeys { it.key ?: "" }
                    ?.mapValues { it.value.toList() }
                    ?: emptyMap(),
                body,
                connection.url.toString()
            )
        }
    }
}
