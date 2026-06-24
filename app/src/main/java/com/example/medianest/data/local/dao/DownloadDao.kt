package com.example.medianest.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.medianest.data.local.entity.DownloadEntity
import com.example.medianest.data.local.entity.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY downloadedAt DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE videoId = :videoId")
    suspend fun getDownloadByVideoId(videoId: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE videoId = :videoId")
    fun getDownloadsForVideoFlow(videoId: String): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getDownloadById(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE videoId = :videoId AND format = :format AND quality = :quality LIMIT 1")
    suspend fun getDownload(videoId: String, format: String, quality: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY downloadedAt ASC")
    fun getDownloadsByStatus(status: DownloadStatus): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'QUEUED' OR status = 'DOWNLOADING'")
    fun getActiveDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'DOWNLOADING' AND format != 'audio_extracted'")
    suspend fun getActiveDownloadCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity): Long

    @Update
    suspend fun update(download: DownloadEntity)

    @Delete
    suspend fun delete(download: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, progress = :progress WHERE id = :id")
    suspend fun updateStatus(id: Long, status: DownloadStatus, progress: Float)

    @Query("UPDATE downloads SET status = :status WHERE id = :id")
    suspend fun updateStatusOnly(id: Long, status: DownloadStatus)

    @Query("UPDATE downloads SET status = :status, errorMessage = :errorMessage, retryCount = :retryCount WHERE id = :id")
    suspend fun markFailed(id: Long, status: DownloadStatus, errorMessage: String, retryCount: Int)

    @Query("UPDATE downloads SET status = 'COMPLETED', progress = 1.0, errorMessage = NULL, fileSizeBytes = :fileSize, filePath = :filePath, downloadedAt = :downloadedAt WHERE id = :id")
    suspend fun markCompleted(id: Long, fileSize: Long, filePath: String, downloadedAt: Long)

    @Query("UPDATE downloads SET progress = :progress, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateProgressAndMessage(id: Long, progress: Float, errorMessage: String?)

    @Query("UPDATE downloads SET fileSizeBytes = :size WHERE id = :id")
    suspend fun updateFileSize(id: Long, size: Long)

    @Query("UPDATE downloads SET retryCount = :retryCount WHERE id = :id")
    suspend fun updateRetryCount(id: Long, retryCount: Int)

    @Query("SELECT * FROM downloads WHERE videoId = :videoId AND status = 'COMPLETED'")
    suspend fun getCompletedDownloadsForVideo(videoId: String): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE videoId = :videoId AND format = 'audio_extracted'")
    suspend fun getAudioExtraction(videoId: String): DownloadEntity?

    @Query("SELECT * FROM downloads")
    suspend fun getAllDownloadsOnce(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE updatedAt > :since")
    suspend fun getDownloadsSince(since: Long): List<DownloadEntity>

    @Query("UPDATE downloads SET status = 'QUEUED' WHERE status = 'DOWNLOADING' AND format != 'audio_extracted'")
    suspend fun resetStaleDownloads()
}
