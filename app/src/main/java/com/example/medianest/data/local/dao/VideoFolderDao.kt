package com.example.medianest.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.medianest.data.local.entity.FolderEntity
import com.example.medianest.data.local.entity.VideoEntity
import com.example.medianest.data.local.entity.VideoFolderJoin
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoFolderDao {
    @Query("SELECT v.* FROM videos v INNER JOIN video_folder_join vfj ON v.id = vfj.videoId WHERE vfj.folderId = :folderId ORDER BY vfj.addedAt DESC")
    fun getVideosInFolder(folderId: Long): Flow<List<VideoEntity>>

    @Query("SELECT COUNT(*) FROM video_folder_join WHERE folderId = :folderId")
    suspend fun getVideoCountInFolder(folderId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addVideoToFolder(join: VideoFolderJoin)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(join: VideoFolderJoin)

    @Query("DELETE FROM video_folder_join WHERE videoId = :videoId AND folderId = :folderId")
    suspend fun removeVideoFromFolder(videoId: String, folderId: Long)

    @Query("SELECT folderId FROM video_folder_join WHERE videoId = :videoId")
    suspend fun getFolderIdsForVideo(videoId: String): List<Long>

    @Query("SELECT f.* FROM folders f INNER JOIN video_folder_join vfj ON f.id = vfj.folderId WHERE vfj.videoId = :videoId")
    fun getFoldersForVideo(videoId: String): Flow<List<FolderEntity>>

    @Query("SELECT * FROM video_folder_join")
    suspend fun getAllJoins(): List<VideoFolderJoin>
}
