package com.example.medianest.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medianest.data.backup.BackupRepository
import com.example.medianest.data.backup.LibraryRepair
import com.example.medianest.data.backup.RestoreRepository
import com.example.medianest.data.preferences.DevicePreferences
import com.example.medianest.data.sync.SyncManager
import com.example.medianest.data.sync.SyncRepository
import com.example.medianest.data.sync.SyncState
import com.example.medianest.data.preferences.DownloadPreferences
import com.example.medianest.worker.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.OutputStream
import javax.inject.Inject
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.room.withTransaction
import com.example.medianest.data.local.AppDatabase
import com.example.medianest.data.local.entity.DownloadStatus
import com.example.medianest.data.repository.DownloadRepository
import com.example.medianest.data.repository.VideoRepository

sealed class ExportImportState {
    data object Idle : ExportImportState()
    data class InProgress(val operation: String, val progress: Float = 0f) : ExportImportState()
    data class Success(val message: String, val details: List<String> = emptyList()) : ExportImportState()
    data class Error(val message: String) : ExportImportState()
}

sealed class ImportInspectionState {
    data object Idle : ImportInspectionState()
    data class NeedsChoice(val uri: Uri) : ImportInspectionState()
}

sealed class MigrationState {
    data object Idle : MigrationState()
    data class InProgress(val currentFile: String, val movedCount: Int, val totalCount: Int, val progress: Float) : MigrationState()
    data class Success(val movedCount: Int) : MigrationState()
    data class Error(val message: String) : MigrationState()
}

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class UpdateAvailable(val latestVersion: String, val changelog: String, val downloadUrl: String) : UpdateState()
    object NoUpdateAvailable : UpdateState()
    data class Downloading(val progress: Float) : UpdateState()
    data class Error(val message: String) : UpdateState()
    object ReadyToInstall : UpdateState()
}

@Serializable
data class GitHubRelease(
    val tag_name: String,
    val body: String? = null,
    val assets: List<GitHubAsset> = emptyList()
)

@Serializable
data class GitHubAsset(
    val name: String,
    val browser_download_url: String
)

@HiltViewModel
class ExportImportViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    private val restoreRepository: RestoreRepository,
    private val libraryRepair: LibraryRepair,
    private val devicePreferences: DevicePreferences,
    private val downloadPreferences: DownloadPreferences,
    private val syncRepository: SyncRepository,
    private val syncManager: SyncManager,
    private val okHttpClient: OkHttpClient,
    private val downloadRepository: DownloadRepository,
    private val videoRepository: VideoRepository,
    private val database: AppDatabase,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow<ExportImportState>(ExportImportState.Idle)
    val state: StateFlow<ExportImportState> = _state

    private val _importInspection = MutableStateFlow<ImportInspectionState>(ImportInspectionState.Idle)
    val importInspection: StateFlow<ImportInspectionState> = _importInspection

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState

    private val json = Json { ignoreUnknownKeys = true }

    val syncState: StateFlow<SyncState> = syncManager.state
    val syncLog = syncManager.log
    val serverUrl = devicePreferences.serverUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val apiKey = devicePreferences.apiKey.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val deviceId = devicePreferences.deviceId.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val lastSyncAt = devicePreferences.lastSyncAt.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)
    val syncIntervalHours = devicePreferences.syncIntervalHours.stateIn(viewModelScope, SharingStarted.Eagerly, 6)

    val downloadFolder = downloadPreferences.downloadFolder.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val _migrationState = MutableStateFlow<MigrationState>(MigrationState.Idle)
    val migrationState: StateFlow<MigrationState> = _migrationState

    var isMigrationCanceled = false
        private set

    fun cancelMigration() {
        isMigrationCanceled = true
    }

    fun resetMigrationState() {
        _migrationState.value = MigrationState.Idle
        isMigrationCanceled = false
    }

    fun startDownloadFolderMigration(newPath: String) {
        isMigrationCanceled = false
        _migrationState.value = MigrationState.InProgress("Preparing", 0, 0, 0f)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val oldPath = downloadFolder.value
                if (oldPath == newPath) {
                    _migrationState.value = MigrationState.Success(0)
                    return@launch
                }

                // Query all completed downloads
                val allDownloads = downloadRepository.getAllDownloadsOnce()
                val downloadsToMove = allDownloads.filter { download ->
                    download.status == DownloadStatus.COMPLETED &&
                    download.filePath.isNotEmpty() &&
                    download.filePath.startsWith(oldPath) &&
                    java.io.File(download.filePath).exists()
                }

                val total = downloadsToMove.size
                if (total == 0) {
                    downloadPreferences.setDownloadFolder(newPath)
                    _migrationState.value = MigrationState.Success(0)
                    return@launch
                }

                var movedCount = 0
                for (download in downloadsToMove) {
                    if (isMigrationCanceled) {
                        break
                    }

                    _migrationState.value = MigrationState.InProgress(
                        currentFile = download.title,
                        movedCount = movedCount,
                        totalCount = total,
                        progress = movedCount.toFloat() / total
                    )

                    val oldFile = java.io.File(download.filePath)
                    val subfolder = if (download.format == "audio" || download.format == "audio_extracted" || oldFile.parentFile?.name == "audio") "audio" else "video"
                    val newDir = java.io.File(newPath, subfolder)
                    newDir.mkdirs()
                    
                    val newFile = java.io.File(newDir, oldFile.name)

                    try {
                        // Copy file safely
                        oldFile.copyTo(newFile, overwrite = true)
                        
                        // Verify file size matches
                        if (newFile.exists() && newFile.length() == oldFile.length()) {
                            // Update DB inside transaction
                            database.withTransaction {
                                // 1. Update DownloadEntity
                                downloadRepository.update(download.copy(filePath = newFile.absolutePath))
                                
                                // 2. Update VideoEntity if localFilePath matches
                                val video = videoRepository.getVideoById(download.videoId)
                                if (video != null && video.localFilePath == oldFile.absolutePath) {
                                    videoRepository.updateVideo(video.copy(localFilePath = newFile.absolutePath))
                                }
                            }
                            // Delete old file only after DB update succeeds
                            oldFile.delete()
                            movedCount++
                        } else {
                            throw Exception("Copy verification failed: file size mismatch")
                        }
                    } catch (e: Exception) {
                        throw Exception("Failed to move '${download.title}': ${e.message}")
                    }
                }

                // Always save the new folder preference, even if cancelled halfway, so the moved files remain correct.
                downloadPreferences.setDownloadFolder(newPath)

                if (isMigrationCanceled) {
                    _migrationState.value = MigrationState.Success(movedCount)
                } else {
                    _migrationState.value = MigrationState.Success(total)
                }
            } catch (e: Exception) {
                _migrationState.value = MigrationState.Error(e.message ?: "Migration failed")
            }
        }
    }

    fun setServerUrl(url: String) { viewModelScope.launch { devicePreferences.setServerUrl(url) } }
    fun setApiKey(key: String) { viewModelScope.launch { devicePreferences.setApiKey(key) } }
    fun setDownloadFolder(path: String) { viewModelScope.launch { downloadPreferences.setDownloadFolder(path) } }

    fun clearSyncLog() { syncManager.clearLog() }

    fun setSyncIntervalHours(hours: Int) {
        viewModelScope.launch {
            devicePreferences.setSyncIntervalHours(hours)
            WorkScheduler.updateSyncInterval(appContext, hours.toLong())
        }
    }

    fun registerDevice(serverUrl: String) {
        viewModelScope.launch {
            val result = syncRepository.register(serverUrl)
            result.onSuccess { response ->
                devicePreferences.setDeviceId(response.deviceId)
                devicePreferences.setApiKey(response.apiKey)
                val interval = devicePreferences.syncIntervalHours.first()
                WorkScheduler.scheduleSync(appContext, interval.toLong())
            }
        }
    }

    fun triggerSync() { viewModelScope.launch { syncManager.sync() } }
    fun resetSyncState() { syncManager.resetState() }

    fun resetImportInspection() {
        _importInspection.value = ImportInspectionState.Idle
    }

    suspend fun getBackupSizes(): Pair<Long, Long> {
        return backupRepository.getBackupSizes()
    }

    fun formatSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(java.util.Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(java.util.Locale.US, "%.2f MB", mb)
            kb >= 1.0 -> String.format(java.util.Locale.US, "%.2f KB", kb)
            else -> "$bytes Bytes"
        }
    }

    fun exportToFile(outputStream: OutputStream, includeMedia: Boolean) {
        _state.value = ExportImportState.InProgress("Exporting", 0f)
        viewModelScope.launch {
            try {
                backupRepository.exportToZip(outputStream, includeMedia) { progress ->
                    _state.value = ExportImportState.InProgress("Exporting", progress)
                }
                _state.value = ExportImportState.Success("Export complete")
            } catch (e: Exception) {
                _state.value = ExportImportState.Error("Export failed: ${e.message}")
            }
        }
    }

    fun inspectImportFile(uri: Uri) {
        _state.value = ExportImportState.InProgress("Checking backup file", 0f)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var hasMedia = false
                appContext.contentResolver.openInputStream(uri)?.use { inputStream ->
                    java.util.zip.ZipInputStream(inputStream).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            if (entry.name.startsWith("media/")) {
                                hasMedia = true
                                break
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                }
                
                if (hasMedia) {
                    _state.value = ExportImportState.Idle
                    _importInspection.value = ImportInspectionState.NeedsChoice(uri)
                } else {
                    _state.value = ExportImportState.Idle
                    restoreFromFile(uri, restoreMedia = false)
                }
            } catch (e: Exception) {
                _state.value = ExportImportState.Error("Inspection failed: ${e.message}")
            }
        }
    }

    fun restoreFromFile(uri: Uri, restoreMedia: Boolean) {
        _state.value = ExportImportState.InProgress("Restoring", 0f)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { inputStream ->
                        restoreRepository.restoreFromZip(inputStream, restoreMedia) { progress ->
                            _state.value = ExportImportState.InProgress("Restoring", progress)
                        }
                    } ?: throw Exception("Could not open file")
                }
                _state.value = ExportImportState.Success("Restore complete")
            } catch (e: Exception) {
                _state.value = ExportImportState.Error("Restore failed: ${e.message}")
            }
        }
    }

    fun repairLibrary() {
        _state.value = ExportImportState.InProgress("Repairing", 0f)
        viewModelScope.launch {
            try {
                val result = libraryRepair.repair { progress ->
                    _state.value = ExportImportState.InProgress("Repairing", progress)
                }
                _state.value = ExportImportState.Success(
                    "Repair: ${result.filesFound} files, ${result.pathsFixed} paths fixed, ${result.orphansRemoved} orphans removed",
                    details = result.details
                )
            } catch (e: Exception) {
                _state.value = ExportImportState.Error("Repair failed: ${e.message}")
            }
        }
    }

    fun resetState() {
        _state.value = ExportImportState.Idle
    }

    fun checkForUpdates() {
        _updateState.value = UpdateState.Checking
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentVersion = try {
                    appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: "1.0"
                } catch (_: Exception) {
                    "1.0"
                }

                val request = Request.Builder()
                    .url("https://api.github.com/repos/kushaldotdev/media-nest/releases/latest")
                    .header("User-Agent", "MediaNest-App")
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("Server returned ${response.code}")
                    val bodyString = response.body?.string() ?: throw Exception("Empty response body")
                    val release = json.decodeFromString<GitHubRelease>(bodyString)
                    val latest = release.tag_name.removePrefix("v").trim()
                    val hasUpdate = isNewerVersion(currentVersion, latest)

                    if (hasUpdate) {
                        val apkAsset = release.assets.find { it.name.endsWith(".apk") }
                        if (apkAsset != null) {
                            _updateState.value = UpdateState.UpdateAvailable(
                                latestVersion = latest,
                                changelog = release.body ?: "No release notes provided.",
                                downloadUrl = apkAsset.browser_download_url
                            )
                        } else {
                            _updateState.value = UpdateState.Error("Update available ($latest) but no APK asset found.")
                        }
                    } else {
                        _updateState.value = UpdateState.NoUpdateAvailable
                    }
                }
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error("Check failed: ${e.message}")
            }
        }
    }

    fun downloadAndInstallUpdate(downloadUrl: String) {
        _updateState.value = UpdateState.Downloading(0f)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(downloadUrl).build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("Download failed: ${response.code}")
                    val body = response.body ?: throw Exception("Response body is empty")
                    val totalBytes = body.contentLength()
                    val file = File(appContext.cacheDir, "update.apk")
                    if (file.exists()) file.delete()

                    body.byteStream().use { input ->
                        file.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var totalBytesRead = 0L
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead
                                if (totalBytes > 0) {
                                    val progress = totalBytesRead.toFloat() / totalBytes.toFloat()
                                    _updateState.value = UpdateState.Downloading(progress)
                                }
                            }
                        }
                    }

                    _updateState.value = UpdateState.ReadyToInstall
                    installApk(file)
                }
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error("Download failed: ${e.message ?: "Unknown error"}")
            }
        }
    }

    private fun installApk(file: File) {
        try {
            val context = appContext
            val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error("Failed to launch package installer: ${e.message}")
        }
    }

    fun resetUpdateState() {
        _updateState.value = UpdateState.Idle
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        val currParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val lateParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(currParts.size, lateParts.size)
        for (i in 0 until maxLen) {
            val currVal = currParts.getOrElse(i) { 0 }
            val lateVal = lateParts.getOrElse(i) { 0 }
            if (lateVal > currVal) return true
            if (currVal > lateVal) return false
        }
        return false
    }
}
