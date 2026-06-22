package com.example.medianest.data.model

data class ExtractedPlaylistInfo(
    val playlistId: String,
    val name: String,
    val thumbnailUrl: String?,
    val uploaderName: String?,
    val videos: List<ExtractedVideoInfo>
)
