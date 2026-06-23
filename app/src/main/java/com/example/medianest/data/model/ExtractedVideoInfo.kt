package com.example.medianest.data.model

data class ExtractedVideoInfo(
    val videoId: String,
    val title: String,
    val channelName: String,
    val channelId: String?,
    val durationSeconds: Long,
    val thumbnailUrl: String?,
    val description: String?,
    val uploadDate: String?,
    val streamSources: List<StreamSource> = emptyList()
)

data class StreamSource(
    val url: String,
    val format: String,
    val quality: String,
    val mimeType: String,
    val codec: String = "",
    val contentLength: Long?
)
