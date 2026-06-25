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
import androidx.compose.foundation.layout.heightIn
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
import com.example.medianest.ui.viewmodel.LocalBackupInfo
import com.example.medianest.ui.viewmodel.MigrationState
import com.example.medianest.ui.viewmodel.ImportInspectionState
import androidx.compose.ui.text.font.FontWeight
import com.example.medianest.ui.components.GlassCard
import com.example.medianest.ui.viewmodel.UpdateState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ExportImportViewModel = hiltViewModel(),
    onNavigateToStatistics: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val downloadFolder by viewModel.downloadFolder.collectAsStateWithLifecycle()
    val migrationState by viewModel.migrationState.collectAsStateWithLifecycle()

    LaunchedEffect(migrationState) {
        when (val s = migrationState) {
            is MigrationState.Success -> {
                if (s.movedCount > 0) {
                    snackbarHostState.showSnackbar("Download folder migrated: moved ${s.movedCount} files")
                } else {
                    snackbarHostState.showSnackbar("Download location updated successfully")
                }
                viewModel.resetMigrationState()
            }
            is MigrationState.Error -> {
                snackbarHostState.showSnackbar("Migration failed: ${s.message}")
                viewModel.resetMigrationState()
            }
            else -> {}
        }
    }

    val defaultDownloadsPath = remember {
        try {
            File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "MediaNest").absolutePath
        } catch (_: Exception) {
            File(context.getExternalFilesDir(null) ?: context.filesDir, "MediaNest").absolutePath
        }
    }

    var customInput by remember(downloadFolder) {
        mutableStateOf(downloadFolder.ifEmpty { defaultDownloadsPath })
    }

    var showUnsupportedPathDialog by remember { mutableStateOf(false) }
    var unsupportedPathMessage by remember { mutableStateOf("") }

    var showExportDialog by remember { mutableStateOf(false) }
    var exportIncludeMedia by remember { mutableStateOf(false) }
    var showRepairDetailsDialog by remember { mutableStateOf(false) }

    var showLocalRestoreDialog by remember { mutableStateOf(false) }
    var localRestoreIncludeMedia by remember { mutableStateOf(false) }
    var localBackupToRestore by remember { mutableStateOf<LocalBackupInfo?>(null) }

    var showLocalDeleteDialog by remember { mutableStateOf(false) }
    var localBackupToDelete by remember { mutableStateOf<LocalBackupInfo?>(null) }

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
                viewModel.exportToFile(outputStream, exportIncludeMedia)
            } catch (_: Exception) { }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.inspectImportFile(it)
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
            
            GlassCard(
                modifier = Modifier.fillMaxWidth()
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
            GlassCard(
                modifier = Modifier.fillMaxWidth()
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
            
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Download Location", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Select where media files should be saved.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = customInput,
                        onValueChange = { customInput = it },
                        label = { Text("Download Path") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { folderPickerLauncher.launch(null) }) {
                                Icon(Icons.Default.FolderOpen, contentDescription = "Choose Folder")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
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
                                viewModel.startDownloadFolderMigration(customInput)
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
                }
            }

            // Section: Data & Backup
            Text("Data Management", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToStatistics
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.BarChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("App Statistics", style = MaterialTheme.typography.titleSmall)
                        Text("View usage data and watch history metrics", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Backup & Restore", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text("Export all data and media files to a ZIP archive, or restore from one.", style = MaterialTheme.typography.bodySmall)
                    
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Notes:\n" +
                               "• Export details: Packages all database records (videos list, subscriptions, watch history, custom folders, playlists) and copies all downloaded video & audio files into a ZIP archive.\n" +
                               "• Import details: Overwrites database with imported records and re-extracts video & audio files to their corresponding local storage paths.\n" +
                               "• Download Missing Files: Appears when files are missing on disk. Clicking it will re-queue and re-download completed files that are absent. Do not run 'Repair Library' first, as it will clear database references to those files.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showExportDialog = true },
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

                    val missingCount by viewModel.missingDownloadsCount.collectAsStateWithLifecycle()
                    if (missingCount > 0) {
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.redownloadMissingFiles() },
                            enabled = state !is ExportImportState.InProgress,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("Download Missing Files ($missingCount)")
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Tip: Re-download missing media files that are completed in the database but not present on disk. Running 'Repair Library' will clear these missing paths, preventing auto-redownloading.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Text("Auto-Backup Settings", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text("Automatically saves metadata-only backups in the 'backup' folder in your download directory.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))

                    val autoInterval by viewModel.autoBackupIntervalHours.collectAsStateWithLifecycle()
                    val autoIntervalOptions = listOf(0, 6, 12, 24, 168)
                    var autoIntervalExpanded by remember { mutableStateOf(false) }

                    ExposedDropdownMenuBox(
                        expanded = autoIntervalExpanded,
                        onExpandedChange = { autoIntervalExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = when (autoInterval) {
                                0 -> "Disabled (Off)"
                                168 -> "Every 7 days"
                                else -> "Every $autoInterval hours"
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Auto-backup interval") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = autoIntervalExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(
                                ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                enabled = true
                            ),
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = autoIntervalExpanded,
                            onDismissRequest = { autoIntervalExpanded = false }
                        ) {
                            autoIntervalOptions.forEach { option ->
                                val text = when (option) {
                                    0 -> "Disabled (Off)"
                                    168 -> "Every 7 days"
                                    else -> "Every $option hours"
                                }
                                DropdownMenuItem(
                                    text = { Text(text) },
                                    onClick = {
                                        viewModel.setAutoBackupIntervalHours(option)
                                        autoIntervalExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    val nextBackupTime by viewModel.nextBackupTime.collectAsStateWithLifecycle(initialValue = null)
                    var countdownText by remember { mutableStateOf("") }

                    if (autoInterval > 0) {
                        LaunchedEffect(nextBackupTime) {
                            while (true) {
                                val nextTime = nextBackupTime
                                if (nextTime != null && nextTime > 0) {
                                    val diff = nextTime - System.currentTimeMillis()
                                    if (diff <= 0) {
                                        countdownText = "Imminent / Running"
                                    } else {
                                        val hours = diff / (1000 * 60 * 60)
                                        val minutes = (diff % (1000 * 60 * 60)) / (1000 * 60)
                                        val seconds = (diff % (1000 * 60)) / 1000
                                        countdownText = String.format(Locale.getDefault(), "%d hr %d min %d sec", hours, minutes, seconds)
                                    }
                                } else {
                                    countdownText = "Disabled"
                                }
                                delay(1000)
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Status: Active", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            if (nextBackupTime != null && nextBackupTime!! > 0) {
                                val dateStr = SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date(nextBackupTime!!))
                                Text("Next Backup: $dateStr (in $countdownText)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text("Status: Disabled", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    val localBackups by viewModel.localBackups.collectAsStateWithLifecycle()
                    if (localBackups.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("Backup Log (Max 3 retained):", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            localBackups.forEach { backup ->
                                val isFull = backup.name.startsWith("backup_full_")
                                val backupType = if (isFull) "Full Backup" else "Metadata"
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = backup.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val formattedTime = remember(backup.lastModified) {
                                            SimpleDateFormat("MMM d, yyyy, h:mm a", Locale.getDefault()).format(Date(backup.lastModified))
                                        }
                                        Text(
                                            text = "Created: $formattedTime ($backupType, ${viewModel.formatSize(backup.sizeBytes)})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            localBackupToRestore = backup
                                            localRestoreIncludeMedia = isFull
                                            showLocalRestoreDialog = true
                                        },
                                        enabled = state !is ExportImportState.InProgress
                                    ) {
                                        Icon(Icons.Default.History, contentDescription = "Restore")
                                    }
                                    IconButton(
                                        onClick = {
                                            localBackupToDelete = backup
                                            showLocalDeleteDialog = true
                                        },
                                        enabled = state !is ExportImportState.InProgress
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            GlassCard(modifier = Modifier.fillMaxWidth()) {
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
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                                            Spacer(Modifier.width(8.dp))
                                            Text(s.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                            IconButton(onClick = { viewModel.resetState() }) {
                                                Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                                            }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        TextButton(
                                            onClick = { showRepairDetailsDialog = true },
                                            modifier = Modifier.align(Alignment.End)
                                        ) {
                                            Text("Show Details")
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
            GlassCard(modifier = Modifier.fillMaxWidth()) {
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
            GlassCard(modifier = Modifier.fillMaxWidth()) {
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

            if (showExportDialog) {
                var sizes by remember { mutableStateOf<Pair<Long, Long>?>(null) }
                LaunchedEffect(Unit) {
                    sizes = viewModel.getBackupSizes()
                }

                val formattedMetadataSize = remember(sizes) {
                    sizes?.first?.let { viewModel.formatSize(it) } ?: "Calculating..."
                }
                val formattedFullSize = remember(sizes) {
                    sizes?.second?.let { viewModel.formatSize(it) } ?: "Calculating..."
                }
                var selectedOption by remember { mutableStateOf(false) } // false = metadata only, true = full backup

                AlertDialog(
                    onDismissRequest = { showExportDialog = false },
                    title = { Text("Export Backup Options") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Choose what data to export:", style = MaterialTheme.typography.bodyMedium)
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedOption = false }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = !selectedOption,
                                    onClick = { selectedOption = false }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("Metadata Only", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "Database and preferences (~$formattedMetadataSize)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedOption = true }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedOption,
                                    onClick = { selectedOption = true }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("Full Backup", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "Includes all downloaded video and audio files (~$formattedFullSize)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                exportIncludeMedia = selectedOption
                                showExportDialog = false
                                exportLauncher.launch(if (selectedOption) "MediaNest_Backup.zip" else "MediaNest_Metadata_Backup.zip")
                            },
                            enabled = sizes != null
                        ) {
                            Text("Export")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showExportDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            val importInspection by viewModel.importInspection.collectAsStateWithLifecycle()
            if (importInspection is ImportInspectionState.NeedsChoice) {
                val uri = (importInspection as ImportInspectionState.NeedsChoice).uri
                var selectedOption by remember { mutableStateOf(true) } // true = full restore, false = metadata only

                AlertDialog(
                    onDismissRequest = { viewModel.resetImportInspection() },
                    title = { Text("Import Options") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("This backup file contains media files. Choose how to restore them:", style = MaterialTheme.typography.bodyMedium)
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedOption = true }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedOption,
                                    onClick = { selectedOption = true }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("Full Restore", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "Restore database, settings, and copy all downloaded media files.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedOption = false }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = !selectedOption,
                                    onClick = { selectedOption = false }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("Metadata Only", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "Restore database and settings, but skip media files.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.restoreFromFile(uri, restoreMedia = selectedOption)
                                viewModel.resetImportInspection()
                            }
                        ) {
                            Text("Restore")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.resetImportInspection() }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (migrationState is MigrationState.InProgress) {
                val s = migrationState as MigrationState.InProgress
                AlertDialog(
                    onDismissRequest = { /* Disallow dismiss by tapping outside */ },
                    title = { Text("Moving Downloaded Files") },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Moving files to new download folder. Please do not close the app.", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(16.dp))
                            Text("Current file: ${s.currentFile}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { s.progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("Moved ${s.movedCount} of ${s.totalCount} files", style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.cancelMigration() }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showLocalRestoreDialog && localBackupToRestore != null) {
                val backup = localBackupToRestore!!
                val isFullBackup = backup.name.startsWith("backup_full_")
                var restoreMedia by remember { mutableStateOf(isFullBackup) }
                AlertDialog(
                    onDismissRequest = { showLocalRestoreDialog = false },
                    title = { Text("Restore Local Backup") },
                    text = {
                        Column {
                            Text("Are you sure you want to restore from ${backup.name}?")
                            Spacer(Modifier.height(8.dp))
                            Text("This will overwrite your current library database records.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            if (isFullBackup) {
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { restoreMedia = !restoreMedia }
                                ) {
                                    Checkbox(checked = restoreMedia, onCheckedChange = { restoreMedia = it })
                                    Spacer(Modifier.width(8.dp))
                                    Text("Restore and overwrite media files", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showLocalRestoreDialog = false
                                viewModel.restoreFromLocalBackup(backup, restoreMedia)
                            }
                        ) {
                            Text("Restore")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLocalRestoreDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showLocalDeleteDialog && localBackupToDelete != null) {
                val backup = localBackupToDelete!!
                AlertDialog(
                    onDismissRequest = { showLocalDeleteDialog = false },
                    title = { Text("Delete Local Backup") },
                    text = {
                        Text("Are you sure you want to delete ${backup.name}? This cannot be undone.")
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showLocalDeleteDialog = false
                                viewModel.deleteLocalBackup(backup)
                            }
                        ) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLocalDeleteDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showRepairDetailsDialog) {
                val repairState = state as? ExportImportState.Success
                val details = repairState?.details ?: emptyList()
                AlertDialog(
                    onDismissRequest = { showRepairDetailsDialog = false },
                    title = { Text("Library Repair Details") },
                    text = {
                        if (details.isEmpty()) {
                            Text("No changes were made. Library is in sync.")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(details) { detail ->
                                    if (detail.isEmpty()) {
                                        Spacer(Modifier.height(8.dp))
                                    } else {
                                        val isHeader = !detail.startsWith(" ")
                                        Text(
                                            text = detail,
                                            style = if (isHeader) {
                                                MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                            } else {
                                                MaterialTheme.typography.bodySmall
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showRepairDetailsDialog = false }) {
                            Text("Close")
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
