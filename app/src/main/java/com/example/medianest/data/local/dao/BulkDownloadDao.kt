package com.example.medianest.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.medianest.data.local.entity.BulkDownloadItemEntity
import com.example.medianest.data.local.entity.BulkDownloadItemStatus
import com.example.medianest.data.local.entity.BulkDownloadJobEntity
import com.example.medianest.data.local.entity.BulkDownloadJobStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface BulkDownloadDao {
    @Insert
    suspend fun insertJob(job: BulkDownloadJobEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<BulkDownloadItemEntity>)

    @Query("SELECT * FROM bulk_download_jobs WHERE id = :jobId LIMIT 1")
    fun observeJob(jobId: Long): Flow<BulkDownloadJobEntity?>

    @Query("SELECT * FROM bulk_download_items WHERE jobId = :jobId ORDER BY displayOrder ASC, id ASC")
    fun observeItems(jobId: Long): Flow<List<BulkDownloadItemEntity>>

    @Query("SELECT * FROM bulk_download_jobs WHERE status IN ('PENDING', 'RUNNING', 'READY', 'FAILED', 'CANCELLED') ORDER BY createdAt DESC LIMIT 1")
    fun observeLatestActiveJob(): Flow<BulkDownloadJobEntity?>

    @Query("SELECT * FROM bulk_download_jobs WHERE id = :jobId LIMIT 1")
    suspend fun getJobOnce(jobId: Long): BulkDownloadJobEntity?

    @Query("SELECT * FROM bulk_download_items WHERE jobId = :jobId ORDER BY displayOrder ASC, id ASC")
    suspend fun getItemsOnce(jobId: Long): List<BulkDownloadItemEntity>

    @Query("UPDATE bulk_download_jobs SET status = :status, processedVideos = :processedVideos, currentTitle = :currentTitle, downloadableVideos = :downloadableVideos, unavailableVideos = :unavailableVideos, failedVideos = :failedVideos, totalSizeBytes = :totalSizeBytes, usableSpaceBytes = :usableSpaceBytes, errorMessage = :errorMessage, updatedAt = :updatedAt WHERE id = :jobId")
    suspend fun updateJobState(
        jobId: Long,
        status: BulkDownloadJobStatus,
        processedVideos: Int,
        currentTitle: String,
        downloadableVideos: Int,
        unavailableVideos: Int,
        failedVideos: Int,
        totalSizeBytes: Long,
        usableSpaceBytes: Long,
        errorMessage: String?,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE bulk_download_jobs SET status = :status, updatedAt = :updatedAt WHERE id = :jobId")
    suspend fun updateJobStatus(jobId: Long, status: BulkDownloadJobStatus, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE bulk_download_jobs SET status = 'CANCELLED', updatedAt = :updatedAt WHERE status IN ('PENDING', 'RUNNING', 'READY')")
    suspend fun cancelActiveJobs(updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE bulk_download_jobs SET status = 'CONFIRMED', updatedAt = :updatedAt WHERE id = :jobId")
    suspend fun markJobConfirmed(jobId: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE bulk_download_jobs SET status = 'FAILED', errorMessage = :errorMessage, updatedAt = :updatedAt WHERE id = :jobId")
    suspend fun markJobFailed(jobId: Long, errorMessage: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE bulk_download_items SET status = :status, quality = :quality, format = :format, codec = :codec, url = :url, contentLengthBytes = :contentLengthBytes, errorMessage = :errorMessage WHERE id = :itemId")
    suspend fun updateItem(
        itemId: Long,
        status: BulkDownloadItemStatus,
        quality: String,
        format: String,
        codec: String,
        url: String,
        contentLengthBytes: Long,
        errorMessage: String?
    )

    @Query("UPDATE bulk_download_items SET status = :status, errorMessage = :errorMessage WHERE id = :itemId")
    suspend fun updateItemStatus(itemId: Long, status: BulkDownloadItemStatus, errorMessage: String? = null)

    @Query("DELETE FROM bulk_download_jobs WHERE status = 'CONFIRMED' OR status = 'FAILED' OR status = 'CANCELLED'")
    suspend fun pruneFinishedJobs()

    @Transaction
    suspend fun createJobWithItems(job: BulkDownloadJobEntity, items: List<BulkDownloadItemEntity>): Long {
        val jobId = insertJob(job)
        val jobItems = items.map { it.copy(jobId = jobId) }
        insertItems(jobItems)
        return jobId
    }

    @Transaction
    suspend fun replaceActiveJobWithItems(job: BulkDownloadJobEntity, items: List<BulkDownloadItemEntity>): Long {
        cancelActiveJobs()
        return createJobWithItems(job, items)
    }
}
