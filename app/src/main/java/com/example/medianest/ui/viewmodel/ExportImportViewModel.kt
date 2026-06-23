package com.example.medianest.ui.viewmodel

import android.content.Context
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

sealed class ExportImportState {
    data object Idle : ExportImportState()
    data class InProgress(val operation: String, val progress: Float = 0f) : ExportImportState()
    data class Success(val message: String) : ExportImportState()
    data class Error(val message: String) : ExportImportState()
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
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow<ExportImportState>(ExportImportState.Idle)
    val state: StateFlow<ExportImportState> = _state

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

    fun exportToFile(outputStream: OutputStream) {
        _state.value = ExportImportState.InProgress("Exporting", 0f)
        viewModelScope.launch {
            try {
                backupRepository.exportToZip(outputStream) { progress ->
                    _state.value = ExportImportState.InProgress("Exporting", progress)
                }
                _state.value = ExportImportState.Success("Export complete")
            } catch (e: Exception) {
                _state.value = ExportImportState.Error("Export failed: ${e.message}")
            }
        }
    }

    fun restoreFromFile(inputStream: java.io.InputStream) {
        _state.value = ExportImportState.InProgress("Restoring", 0f)
        viewModelScope.launch {
            try {
                restoreRepository.restoreFromZip(inputStream) { progress ->
                    _state.value = ExportImportState.InProgress("Restoring", progress)
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
                    "Repair: ${result.filesFound} files, ${result.pathsFixed} paths fixed, ${result.orphansRemoved} orphans removed"
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
