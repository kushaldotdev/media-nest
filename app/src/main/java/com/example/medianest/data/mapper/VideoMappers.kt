package com.example.medianest.data.mapper

import com.example.medianest.data.local.entity.VideoEntity
import com.example.medianest.data.model.ExtractedVideoInfo

fun ExtractedVideoInfo.toVideoEntity(): VideoEntity = VideoEntity(
    id = videoId,
    title = title,
    channelName = channelName,
    channelId = channelId,
    durationSeconds = durationSeconds,
    thumbnailUrl = thumbnailUrl,
    description = description,
    uploadDate = uploadDate,
    addedAt = System.currentTimeMillis()
)
