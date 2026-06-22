package com.example.medianest.worker

import android.content.Context
import android.content.Intent
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.medianest.data.local.entity.DownloadEntity
import com.example.medianest.data.local.entity.DownloadStatus
import com.example.medianest.data.local.dao.VideoDao
import com.example.medianest.data.repository.DownloadRepository
import com.example.medianest.data.repository.SubscriptionRepository
import com.example.medianest.extraction.YouTubeExtractor
import com.example.medianest.service.DownloadService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class SubscriptionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val subscriptionRepository: SubscriptionRepository,
    private val downloadRepository: DownloadRepository,
    private val youTubeExtractor: YouTubeExtractor,
    private val videoDao: VideoDao
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val subscriptions = subscriptionRepository.getAllSubscriptions().first()

        for (sub in subscriptions) {
            try {
                val newVideos = subscriptionRepository.checkForUpdates(sub)
                if (newVideos.isNotEmpty() && sub.autoDownload) {
                    for (video in newVideos) {
                        try {
                            val url = "https://youtube.com/watch?v=${video.id}"
                            val info = youTubeExtractor.extractVideo(url)
                            val streams = if (sub.audioOnly) {
                                info.streamSources.filter { it.format == "audio" }
                            } else {
                                info.streamSources.filter { it.format == "video" || it.format == "video_only" }
                            }
                            val best = streams.maxByOrNull { parseQuality(it.quality) }
                            if (best != null) {
                                val existing = downloadRepository.getDownload(video.id, best.format, best.quality)
                                if (existing == null) {
                                    val entity = DownloadEntity(
                                        videoId = video.id,
                                        url = best.url,
                                        format = best.format,
                                        quality = best.quality,
                                        status = DownloadStatus.QUEUED,
                                        title = video.title,
                                        thumbnailUrl = video.thumbnailUrl
                                    )
                                    downloadRepository.insert(entity)
                                    applicationContext.startForegroundService(
                                        Intent(applicationContext, DownloadService::class.java)
                                    )
                                }
                            }
                        } catch (_: Exception) {
                            // Skip video if extraction fails
                        }
                    }
                }
            } catch (_: Exception) {
                // Skip subscription if check fails
            }
        }
        return Result.success()
    }

    private fun parseQuality(quality: String): Int {
        return quality.filter { it.isDigit() }.toIntOrNull() ?: 0
    }
}
