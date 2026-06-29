package com.example.medianest.data.model

data class ExtractedPlaylistInfo(
    val playlistId: String,
    val name: String,
    val thumbnailUrl: String?,
    val uploaderName: String?,
    val videoCount: Int,
    val videos: List<ExtractedVideoInfo>
)
