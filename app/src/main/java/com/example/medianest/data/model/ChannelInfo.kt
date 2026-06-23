package com.example.medianest.data.model

data class ChannelInfo(
    val channelId: String,
    val url: String,
    val name: String,
    val avatarUrl: String?,
    val subscriberCount: Long?,
    val description: String?,
    val uploads: List<ExtractedVideoInfo>
)
