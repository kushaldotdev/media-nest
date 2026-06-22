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

sealed class ExportImportState {
    data object Idle : ExportImportState()
    data class InProgress(val operation: String, val progress: Float = 0f) : ExportImportState()
    data class Success(val message: String) : ExportImportState()
    data class Error(val message: String) : ExportImportState()
}

@HiltViewModel
class ExportImportViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    private val restoreRepository: RestoreRepository,
    private val libraryRepair: LibraryRepair,
    private val devicePreferences: DevicePreferences,
    private val syncRepository: SyncRepository,
    private val syncManager: SyncManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow<ExportImportState>(ExportImportState.Idle)
    val state: StateFlow<ExportImportState> = _state

    val syncState: StateFlow<SyncState> = syncManager.state
    val syncLog = syncManager.log
    val serverUrl = devicePreferences.serverUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val apiKey = devicePreferences.apiKey.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val deviceId = devicePreferences.deviceId.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val lastSyncAt = devicePreferences.lastSyncAt.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)
    val syncIntervalHours = devicePreferences.syncIntervalHours.stateIn(viewModelScope, SharingStarted.Eagerly, 6)

    fun setServerUrl(url: String) { viewModelScope.launch { devicePreferences.setServerUrl(url) } }
    fun setApiKey(key: String) { viewModelScope.launch { devicePreferences.setApiKey(key) } }

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
}
