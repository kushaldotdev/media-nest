package com.example.medianest.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medianest.data.backup.BackupRepository
import com.example.medianest.data.backup.LibraryRepair
import com.example.medianest.data.backup.RestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.FileOutputStream
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
    private val libraryRepair: LibraryRepair
) : ViewModel() {

    private val _state = MutableStateFlow<ExportImportState>(ExportImportState.Idle)
    val state: StateFlow<ExportImportState> = _state

    fun exportToFile(outputStream: FileOutputStream) {
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
