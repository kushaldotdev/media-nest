package com.example.medianest.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.medianest.data.sync.SyncLogEntry
import com.example.medianest.data.sync.SyncState
import com.example.medianest.ui.viewmodel.ExportImportState
import com.example.medianest.ui.viewmodel.ExportImportViewModel
import kotlinx.coroutines.delay
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ExportImportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        uri?.let {
            try {
                val outputStream = context.contentResolver.openOutputStream(it) ?: return@let
                viewModel.exportToFile(outputStream)
            } catch (_: Exception) { }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it) ?: return@let
                viewModel.restoreFromFile(inputStream)
            } catch (_: Exception) { }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Backup", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Export all data and media files to a ZIP archive.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { exportLauncher.launch("MediaNest_Backup.zip") },
                        enabled = state !is ExportImportState.InProgress,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Export Backup")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Restore", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Import from a previously exported ZIP archive.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { importLauncher.launch(arrayOf("application/zip")) },
                        enabled = state !is ExportImportState.InProgress,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Import Backup")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Library Repair", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Scan media files and fix missing paths. Removes orphan files.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.repairLibrary() },
                        enabled = state !is ExportImportState.InProgress,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Repair Library")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Sync",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
                    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()

                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { viewModel.setServerUrl(it) },
                        label = { Text("Server URL") },
                        placeholder = { Text("https://example.com:8000") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { viewModel.setApiKey(it) },
                        label = { Text("API Key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.registerDevice(serverUrl) },
                            enabled = serverUrl.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Register Device")
                        }

                        Button(
                            onClick = { viewModel.triggerSync() },
                            enabled = serverUrl.isNotBlank() && apiKey.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sync Now")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val interval by viewModel.syncIntervalHours.collectAsStateWithLifecycle()
                    val intervalOptions = listOf(1, 2, 6, 12, 24)
                    var intervalExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = intervalExpanded,
                        onExpandedChange = { intervalExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = "Every $interval ${if (interval == 1) "hour" else "hours"}",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Auto-sync interval") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = intervalExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(
                                androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                enabled = true
                            ),
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = intervalExpanded,
                            onDismissRequest = { intervalExpanded = false }
                        ) {
                            intervalOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text("Every $option ${if (option == 1) "hour" else "hours"}") },
                                    onClick = {
                                        viewModel.setSyncIntervalHours(option)
                                        intervalExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
                    LaunchedEffect(syncState) {
                        if (syncState is SyncState.Success || syncState is SyncState.Error) {
                            delay(3000)
                            viewModel.resetSyncState()
                        }
                    }

                    when (val s = syncState) {
                        is SyncState.Syncing -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("Syncing...", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        is SyncState.Success -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(s.message, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        is SyncState.Error -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(s.message, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error)
                        }
                        else -> {}
                    }

                    val lastSyncAt by viewModel.lastSyncAt.collectAsStateWithLifecycle()
                    if (lastSyncAt > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                            .format(Date(lastSyncAt))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudOff, contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Last synced: $date", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    val deviceId by viewModel.deviceId.collectAsStateWithLifecycle()
                    if (deviceId.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Device ID: $deviceId", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            var logExpanded by remember { mutableStateOf(false) }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Sync Log", style = MaterialTheme.typography.titleSmall)
                        Row {
                            if (logExpanded && viewModel.syncLog.value.isNotEmpty()) {
                                TextButton(onClick = { viewModel.clearSyncLog() }) {
                                    Text("Clear", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            TextButton(onClick = { logExpanded = !logExpanded }) {
                                Text(if (logExpanded) "Hide" else "Show",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (logExpanded) {
                        val log by viewModel.syncLog.collectAsStateWithLifecycle()
                        if (log.isEmpty()) {
                            Text("No sync activity yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                                items(log.take(50)) { entry ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(entry.formattedTime,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.width(52.dp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        val icon = when (entry.type) {
                                            "error" -> Icons.Default.Warning
                                            "push" -> Icons.Default.CloudUpload
                                            "pull" -> Icons.Default.CloudDownload
                                            "apply" -> Icons.Default.Edit
                                            else -> Icons.Default.Info
                                        }
                                        val tint = when (entry.type) {
                                            "error" -> MaterialTheme.colorScheme.error
                                            "apply" -> MaterialTheme.colorScheme.secondary
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                        Icon(icon, contentDescription = null,
                                            modifier = Modifier.size(14.dp), tint = tint)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text((entry.table?.let { "[$it] " } ?: "") + entry.summary,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (val s = state) {
                is ExportImportState.InProgress -> {
                    Spacer(Modifier.height(16.dp))
                    Text("${s.operation}...", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { s.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is ExportImportState.Success -> {
                    Spacer(Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(s.message, modifier = Modifier.padding(16.dp))
                    }
                }
                is ExportImportState.Error -> {
                    Spacer(Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(s.message, modifier = Modifier.padding(16.dp))
                    }
                }
                else -> {}
            }
        }
    }
}
