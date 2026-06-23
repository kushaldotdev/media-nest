package com.example.medianest.ui.screens

import android.net.Uri
import android.provider.DocumentsContract
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.medianest.data.sync.SyncLogEntry
import com.example.medianest.data.sync.SyncState
import com.example.medianest.ui.viewmodel.ExportImportState
import com.example.medianest.ui.viewmodel.ExportImportViewModel
import com.example.medianest.ui.viewmodel.UpdateState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val downloadFolder by viewModel.downloadFolder.collectAsStateWithLifecycle()
    val externalPath = context.getExternalFilesDir(null)?.absolutePath ?: ""
    val internalPath = context.filesDir.absolutePath
    val defaultDownloadsPath = remember {
        try {
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).absolutePath
        } catch (_: Exception) {
            "/storage/emulated/0/Download"
        }
    }

    var customInput by remember(downloadFolder) {
        mutableStateOf(
            if (downloadFolder.isNotEmpty() && downloadFolder != externalPath) downloadFolder else defaultDownloadsPath
        )
    }

    var showUnsupportedPathDialog by remember { mutableStateOf(false) }
    var unsupportedPathMessage by remember { mutableStateOf("") }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val path = getPathFromUri(context, it)
            if (path != null) {
                customInput = path
            } else {
                unsupportedPathMessage = "Could not resolve absolute path from selected folder. Please enter it manually."
                showUnsupportedPathDialog = true
            }
        }
    }

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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    focusManager.clearFocus()
                }
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section: Sync & VPS Configuration
            Text("VPS Sync", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
                    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()

                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { viewModel.setServerUrl(it) },
                        label = { Text("VPS Server URL") },
                        placeholder = { Text("https://your-vps-ip:8000") },
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
                                ExposedDropdownMenuAnchorType.PrimaryNotEditable,
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
                            Text("Syncing with VPS...", style = MaterialTheme.typography.bodySmall,
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
                        Spacer(modifier = Modifier.height(8.dp))
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

            // Sync Log
            var logExpanded by remember { mutableStateOf(false) }
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                            LazyColumn(modifier = Modifier.fillMaxWidth().height(150.dp)) {
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

            // Section: Download Folder Location
            Text("Downloads", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Download Location", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Select where media files should be saved.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))

                    var selectedOption by remember(downloadFolder) {
                        mutableStateOf(
                            when (downloadFolder) {
                                "" -> "internal"
                                externalPath -> "external"
                                else -> "custom"
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedOption == "internal",
                            onClick = {
                                selectedOption = "internal"
                                viewModel.setDownloadFolder("")
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Download location set to Internal storage")
                                }
                            },
                            label = { Text("Internal") }
                        )
                        if (externalPath.isNotEmpty()) {
                            FilterChip(
                                selected = selectedOption == "external",
                                onClick = {
                                    selectedOption = "external"
                                    viewModel.setDownloadFolder(externalPath)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Download location set to External storage")
                                    }
                                },
                                label = { Text("External") }
                            )
                        }
                        FilterChip(
                            selected = selectedOption == "custom",
                            onClick = {
                                selectedOption = "custom"
                            },
                            label = { Text("Custom") }
                        )
                    }

                    if (selectedOption == "custom") {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customInput,
                            onValueChange = { customInput = it },
                            label = { Text("Custom Absolute Path") },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { folderPickerLauncher.launch(null) }) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = "Choose Folder")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(defaultDownloadsPath) }
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val targetDir = java.io.File(customInput)
                                var isSupported = false
                                try {
                                    if (!targetDir.exists()) {
                                        targetDir.mkdirs()
                                    }
                                    isSupported = targetDir.exists() && targetDir.canWrite()
                                    if (isSupported) {
                                        val testFile = java.io.File(targetDir, ".tmp_write_test")
                                        if (testFile.createNewFile()) {
                                            testFile.delete()
                                        } else {
                                            isSupported = false
                                        }
                                    }
                                } catch (e: Exception) {
                                    isSupported = false
                                }

                                if (isSupported) {
                                    viewModel.setDownloadFolder(customInput)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Download location updated successfully")
                                    }
                                } else {
                                    unsupportedPathMessage = "The folder path '$customInput' is not writable or supported. On Android 10+ (API 29+), writing to public system directories is restricted. Please select a different directory."
                                    showUnsupportedPathDialog = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Apply Location")
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Warning: Ensure the path is writable by the app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Path: ${downloadFolder.ifEmpty { internalPath }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Section: Data & Backup
            Text("Data Management", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Backup & Restore", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text("Export all data and media files to a ZIP archive, or restore from one.", style = MaterialTheme.typography.bodySmall)
                    
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Notes:\n" +
                               "• Export details: Packages all database records (videos list, subscriptions, watch history, custom folders, playlists) and copies all downloaded video & audio files into a ZIP archive.\n" +
                               "• Import details: Overwrites database with imported records and re-extracts video & audio files to their corresponding local storage paths.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { exportLauncher.launch("MediaNest_Backup.zip") },
                            enabled = state !is ExportImportState.InProgress,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Export Backup")
                        }
                        Button(
                            onClick = { importLauncher.launch(arrayOf("application/zip")) },
                            enabled = state !is ExportImportState.InProgress,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Import Backup")
                        }
                    }

                    // Backup & Restore status inside the card
                    val isBackupRestoreState = remember(state) {
                        when (val s = state) {
                            is ExportImportState.InProgress -> s.operation == "Exporting" || s.operation == "Restoring"
                            is ExportImportState.Success -> s.message.startsWith("Export complete") || s.message.startsWith("Restore complete")
                            is ExportImportState.Error -> s.message.startsWith("Export failed") || s.message.startsWith("Restore failed")
                            else -> false
                        }
                    }

                    if (isBackupRestoreState) {
                        Spacer(Modifier.height(12.dp))
                        when (val s = state) {
                            is ExportImportState.InProgress -> {
                                Text("${s.operation}... ${(s.progress * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { s.progress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            is ExportImportState.Success -> {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                                        Spacer(Modifier.width(8.dp))
                                        Text(s.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                        IconButton(onClick = { viewModel.resetState() }) {
                                            Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                            is ExportImportState.Error -> {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                        Spacer(Modifier.width(8.dp))
                                        Text(s.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                                        IconButton(onClick = { viewModel.resetState() }) {
                                            Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Library Repair", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text("Scan media files and fix missing paths. Removes orphan files.", style = MaterialTheme.typography.bodySmall)
                    
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Notes:\n" +
                               "• Repair details: Scans video/audio storage directories on disk. If a video is on disk but has an incorrect path in the database, it fixes the path. If a video in the database is missing on disk, it clears its offline status. Any files on disk not linked to any database video or download are deleted as orphans. It does NOT search for or add new videos to your library.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.repairLibrary() },
                        enabled = state !is ExportImportState.InProgress,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Repair Library")
                    }

                    // Repair status inside the card
                    val isRepairState = remember(state) {
                        when (val s = state) {
                            is ExportImportState.InProgress -> s.operation == "Repairing"
                            is ExportImportState.Success -> s.message.startsWith("Repair:")
                            is ExportImportState.Error -> s.message.startsWith("Repair failed:")
                            else -> false
                        }
                    }

                    if (isRepairState) {
                        Spacer(Modifier.height(12.dp))
                        when (val s = state) {
                            is ExportImportState.InProgress -> {
                                Text("Repairing... ${(s.progress * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { s.progress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            is ExportImportState.Success -> {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                                        Spacer(Modifier.width(8.dp))
                                        Text(s.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                        IconButton(onClick = { viewModel.resetState() }) {
                                            Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                            is ExportImportState.Error -> {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                        Spacer(Modifier.width(8.dp))
                                        Text(s.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                                        IconButton(onClick = { viewModel.resetState() }) {
                                            Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }

            // Section: About & Updates
            Text("About", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            val updateState by viewModel.updateState.collectAsStateWithLifecycle()
            val currentAppVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
            } catch (_: Exception) {
                "1.0"
            }

            // Card 1: About App info
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("MediaNest App", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "A premium offline-first media manager and subscription player designed to organize, save, and stream your favorite content seamlessly.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Author: Kushal", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("Version: v$currentAppVersion", style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Card 2: App Updates
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("App Updates", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Current Installed Version: v$currentAppVersion", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))

                    when (val s = updateState) {
                        is UpdateState.Idle -> {
                            Button(
                                onClick = { viewModel.checkForUpdates() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Check for Updates")
                            }
                        }
                        is UpdateState.Checking -> {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(12.dp))
                                Text("Checking GitHub releases...", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        is UpdateState.UpdateAvailable -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("New Version Available: v${s.latestVersion}", style = MaterialTheme.typography.titleSmall)
                                        Spacer(Modifier.height(4.dp))
                                        Text(s.changelog, style = MaterialTheme.typography.bodySmall, maxLines = 5, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.downloadAndInstallUpdate(s.downloadUrl) },
                                        modifier = Modifier.weight(1.5f)
                                    ) {
                                        Text("Download & Install")
                                    }
                                    TextButton(
                                        onClick = { viewModel.resetUpdateState() },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Cancel")
                                    }
                                }
                            }
                        }
                        is UpdateState.NoUpdateAvailable -> {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("You are on the latest version.", style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { viewModel.resetUpdateState() }) {
                                    Text("Dismiss")
                                }
                            }
                        }
                        is UpdateState.Downloading -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                val progressPercent = (s.progress * 100).toInt()
                                Text("Downloading: $progressPercent%", style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { s.progress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        is UpdateState.ReadyToInstall -> {
                            Text("Launching installer...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        is UpdateState.Error -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Update check failed", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleSmall)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(s.message, style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = { viewModel.checkForUpdates() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Try Again")
                                }
                            }
                        }
                    }
                }
            }

            if (showUnsupportedPathDialog) {
                AlertDialog(
                    onDismissRequest = { showUnsupportedPathDialog = false },
                    title = { Text("Unsupported Location") },
                    text = { Text(unsupportedPathMessage) },
                    confirmButton = {
                        TextButton(onClick = { showUnsupportedPathDialog = false }) {
                            Text("OK")
                        }
                    }
                )
            }

            // Localized state displays are used instead of a global overlay at the bottom
        }
    }
}

private fun getPathFromUri(context: android.content.Context, uri: Uri): String? {
    try {
        val authority = uri.authority
        val docId = DocumentsContract.getTreeDocumentId(uri)
        if ("com.android.externalstorage.documents" == authority) {
            val split = docId.split(":")
            if (split.size >= 2) {
                val type = split[0]
                val relativePath = split[1]
                if ("primary".equals(type, ignoreCase = true)) {
                    return android.os.Environment.getExternalStorageDirectory().absolutePath + "/" + relativePath
                } else {
                    val extDirs = context.getExternalFilesDirs(null)
                    for (extDir in extDirs) {
                        val path = extDir.absolutePath
                        val rootIndex = path.indexOf("/Android/data")
                        if (rootIndex != -1) {
                            val rootPath = path.substring(0, rootIndex)
                            val sdCardFile = java.io.File(rootPath + "/" + relativePath)
                            if (sdCardFile.exists() || sdCardFile.mkdirs()) {
                                return sdCardFile.absolutePath
                            }
                        }
                    }
                }
            }
        } else if ("com.android.providers.downloads.documents" == authority) {
            if (docId.startsWith("raw:")) {
                return docId.substring(4)
            }
            if (docId == "downloads" || docId == "downloads-list") {
                return android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).absolutePath
            }
            if (docId.startsWith("downloads:")) {
                return android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).absolutePath
            }
            val idx = docId.indexOf("/storage/emulated/")
            if (idx != -1) return docId.substring(idx)
        } else if ("com.android.providers.media.documents" == authority) {
            val split = docId.split(":")
            if (split.size >= 2) {
                val type = split[0]
                val relativePath = split[1]
                if ("primary".equals(type, ignoreCase = true)) {
                    return android.os.Environment.getExternalStorageDirectory().absolutePath + "/" + relativePath
                }
            }
        }
        
        if (docId.startsWith("raw:")) {
            return docId.substring(4)
        }
        if (docId.contains("primary:")) {
            val idx = docId.indexOf("primary:")
            return android.os.Environment.getExternalStorageDirectory().absolutePath + "/" + docId.substring(idx + 8)
        }
        val idx = docId.indexOf("/storage/emulated/")
        if (idx != -1) return docId.substring(idx)
    } catch (_: Exception) { }
    return null
}
