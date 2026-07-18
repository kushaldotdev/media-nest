package com.example.medianest.data.repository

import com.example.medianest.data.local.dao.DownloadDao
import com.example.medianest.data.local.entity.DownloadEntity
import com.example.medianest.data.local.entity.DownloadStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    private val downloadDao: DownloadDao
) {
    fun getAllDownloads(): Flow<List<DownloadEntity>> = downloadDao.getAllDownloads()

    fun getActiveDownloads(): Flow<List<DownloadEntity>> = downloadDao.getActiveDownloads()

    fun getDownloadsByStatus(status: DownloadStatus): Flow<List<DownloadEntity>> =
        downloadDao.getDownloadsByStatus(status)

    suspend fun getDownload(videoId: String, format: String, quality: String): DownloadEntity? =
        downloadDao.getDownload(videoId, format, quality)

    suspend fun getDownloadById(id: Long): DownloadEntity? = downloadDao.getDownloadById(id)

    suspend fun getActiveDownloadCount(): Int = downloadDao.getActiveDownloadCount()

    suspend fun insert(download: DownloadEntity): Long = downloadDao.insert(download)

    suspend fun update(download: DownloadEntity) = downloadDao.update(download)

    suspend fun delete(download: DownloadEntity) = downloadDao.delete(download)

    suspend fun updateStatus(id: Long, status: DownloadStatus, progress: Float) =
        downloadDao.updateStatus(id, status, progress, System.currentTimeMillis())

    suspend fun updateStatusOnly(id: Long, status: DownloadStatus) =
        downloadDao.updateStatusOnly(id, status, System.currentTimeMillis())

    suspend fun markFailed(id: Long, errorMessage: String, retryCount: Int) =
        downloadDao.markFailed(id, DownloadStatus.FAILED, errorMessage, retryCount, System.currentTimeMillis())

    suspend fun markCompleted(id: Long, fileSize: Long, filePath: String) =
        downloadDao.markCompleted(id, fileSize, filePath, System.currentTimeMillis(), System.currentTimeMillis())

    suspend fun updateProgressAndMessage(id: Long, progress: Float, errorMessage: String?) =
        downloadDao.updateProgressAndMessage(id, progress, errorMessage, System.currentTimeMillis())

    suspend fun updateFileSize(id: Long, size: Long) =
        downloadDao.updateFileSize(id, size, System.currentTimeMillis())

    suspend fun updateRetryCount(id: Long, retryCount: Int) =
        downloadDao.updateRetryCount(id, retryCount, System.currentTimeMillis())

    suspend fun updateUrl(id: Long, url: String) =
        downloadDao.updateUrl(id, url, System.currentTimeMillis())

    suspend fun getLocalDownloadsForVideo(videoId: String): List<DownloadEntity> =
        downloadDao.getCompletedDownloadsForVideo(videoId)

    suspend fun getAudioExtraction(videoId: String): DownloadEntity? =
        downloadDao.getAudioExtraction(videoId)

    fun getDownloadsForVideoFlow(videoId: String): Flow<List<DownloadEntity>> =
        downloadDao.getDownloadsForVideoFlow(videoId)

    suspend fun getAllDownloadsOnce(): List<DownloadEntity> = downloadDao.getAllDownloadsOnce()

    suspend fun resetStaleDownloads() = downloadDao.resetStaleDownloads(System.currentTimeMillis())

    suspend fun cancelStaleExtractions() = downloadDao.cancelStaleExtractions(System.currentTimeMillis())
}
