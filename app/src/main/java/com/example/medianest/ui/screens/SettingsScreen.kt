package com.example.medianest.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.medianest.BuildConfig
import com.example.medianest.R
import com.example.medianest.data.preferences.CollectionsPreferences
import com.example.medianest.data.preferences.DownloadPreferences
import com.example.medianest.data.preferences.PlaybackPreferences
import com.example.medianest.data.preferences.SubscriptionsPreferences
import com.example.medianest.data.sync.SyncLogEntry
import com.example.medianest.data.sync.SyncState
import com.example.medianest.ui.components.GlassCard
import com.example.medianest.ui.components.MediaNestButton
import com.example.medianest.ui.components.MediaNestButtonSize
import com.example.medianest.ui.components.MediaNestButtonVariant
import com.example.medianest.ui.components.MediaNestChip
import com.example.medianest.ui.components.MediaNestIconButton
import com.example.medianest.ui.components.MediaNestIconButtonSize
import com.example.medianest.ui.components.MediaNestSnackbarHost
import com.example.medianest.ui.components.MediaNestTopAppBar
import com.example.medianest.ui.components.mediaNestSwitchColors
import com.example.medianest.ui.components.NotificationBellAction
import com.example.medianest.ui.components.MnNoteBox
import com.example.medianest.ui.components.NoteBoxVariant
import com.example.medianest.ui.theme.MediaNestColors
import com.example.medianest.ui.theme.MediaNestShapes
import com.example.medianest.ui.utils.UiUtils
import com.example.medianest.ui.viewmodel.ExportImportState
import com.example.medianest.ui.viewmodel.ExportImportViewModel
import com.example.medianest.ui.viewmodel.ImportInspectionState
import com.example.medianest.ui.viewmodel.LocalBackupInfo
import com.example.medianest.ui.viewmodel.MigrationState
import com.example.medianest.ui.viewmodel.OrphanFile
import com.example.medianest.updates.UpdateState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ExportImportViewModel = hiltViewModel(),
    onNavigateToStatistics: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val applicationContext = context.applicationContext
    val downloadPreferences = remember { DownloadPreferences(applicationContext) }
    val subscriptionsPreferences = remember { SubscriptionsPreferences(applicationContext) }
    val collectionsPreferences = remember { CollectionsPreferences(applicationContext) }
    val playbackPreferences = remember { PlaybackPreferences(applicationContext) }
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val downloadFolder by viewModel.downloadFolder.collectAsStateWithLifecycle()
    val migrationState by viewModel.migrationState.collectAsStateWithLifecycle()
    val orphanFiles by viewModel.orphanFiles.collectAsStateWithLifecycle()
    val isScanningOrphans by viewModel.isScanningOrphans.collectAsStateWithLifecycle()
    var hasScannedOrphans by remember { mutableStateOf(false) }
    var showBrokenFilesDialog by remember { mutableStateOf(false) }

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
            File(
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                "MediaNest"
            ).absolutePath
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
            MediaNestTopAppBar(
                title = "Settings",
                subtitle = "App preferences & data management",
                actions = {
                    NotificationBellAction(onClick = onNavigateToNotifications)
                }
            )
        },
        snackbarHost = { MediaNestSnackbarHost(snackbarHostState) },
        containerColor = MediaNestColors.Background
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // =========================================================================
            // 1. DOWNLOADS & NETWORK
            // =========================================================================
            SettingsSectionHeader(
                title = "Downloads & Network",
                iconRes = R.drawable.ic_mn_download,
                subtitle = "Storage directory, resolution defaults & concurrency"
            )

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    MnNoteBox(
                        title = "Downloads & Storage Rules",
                        variant = NoteBoxVariant.STANDARD,
                        iconPainter = painterResource(R.drawable.ic_mn_download)
                    ) {
                        Text(
                            "Configure storage destination, stream resolution defaults, and background network concurrency limits."
                        )
                    }

                    // Download Storage Location
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.ic_mn_folder),
                            contentDescription = null,
                            tint = MediaNestColors.Accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Download Location",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MediaNestColors.TextPrimary
                        )
                    }

                    Text(
                        "Directory where video and audio files are stored locally.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = MediaNestColors.TextSecondary
                    )

                    OutlinedTextField(
                        value = customInput,
                        onValueChange = { customInput = it },
                        label = { Text("Storage Directory") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { folderPickerLauncher.launch(null) }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_folder_add),
                                    contentDescription = "Choose Folder",
                                    tint = MediaNestColors.Accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = customTextFieldColors(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    MediaNestButton(
                        text = "Apply Location",
                        onClick = {
                            val targetDir = File(customInput)
                            var isSupported = false
                            try {
                                if (!targetDir.exists()) {
                                    targetDir.mkdirs()
                                }
                                isSupported = targetDir.exists() && targetDir.canWrite()
                                if (isSupported) {
                                    val testFile = File(targetDir, ".tmp_write_test")
                                    if (testFile.createNewFile()) {
                                        testFile.delete()
                                    } else {
                                        isSupported = false
                                    }
                                }
                            } catch (_: Exception) {
                                isSupported = false
                            }

                            if (isSupported) {
                                viewModel.startDownloadFolderMigration(customInput)
                            } else {
                                unsupportedPathMessage = "The folder path '$customInput' is not writable or supported. On Android 10+ (API 29+), writing to public system directories is restricted. Please select an app-accessible directory."
                                showUnsupportedPathDialog = true
                            }
                        },
                        variant = MediaNestButtonVariant.Primary,
                        size = MediaNestButtonSize.Standard,
                        fullWidth = true
                    )

                    HorizontalDivider(color = MediaNestColors.Border, thickness = 1.dp)

                    // Default Quality Dropdown
                    val defaultResolution by downloadPreferences.defaultResolution.collectAsStateWithLifecycle(
                        initialValue = DownloadPreferences.DEFAULT_RESOLUTION
                    )
                    val resolutionOptions = listOf("1080p", "720p", "480p", "360p", "Audio")
                    var resolutionExpanded by remember { mutableStateOf(false) }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.ic_mn_video),
                            contentDescription = null,
                            tint = MediaNestColors.Accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Default Download Quality",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MediaNestColors.TextPrimary
                        )
                    }

                    ExposedDropdownMenuBox(
                        expanded = resolutionExpanded,
                        onExpandedChange = { resolutionExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = defaultResolution,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Quality Preset") },
                            trailingIcon = {
                                Icon(
                                    painter = painterResource(if (resolutionExpanded) R.drawable.ic_mn_chevron_up else R.drawable.ic_mn_chevron_down),
                                    contentDescription = null,
                                    tint = MediaNestColors.TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(
                                    ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                    enabled = true
                                ),
                            colors = customTextFieldColors(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = resolutionExpanded,
                            onDismissRequest = { resolutionExpanded = false },
                            containerColor = MediaNestColors.Card
                        ) {
                            resolutionOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            option,
                                            color = if (option == defaultResolution) MediaNestColors.Accent else MediaNestColors.TextPrimary
                                        )
                                    },
                                    onClick = {
                                        coroutineScope.launch {
                                            downloadPreferences.setDefaultResolution(option)
                                        }
                                        resolutionExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MediaNestColors.Border, thickness = 1.dp)

                    // Max Concurrent Downloads Control (1-5)
                    val maxConcurrent by downloadPreferences.maxConcurrentDownloads.collectAsStateWithLifecycle(
                        initialValue = DownloadPreferences.DEFAULT_MAX
                    )
                    val maxConcurrentOptions = listOf(1, 2, 3, 4, 5)
                    var maxConcurrentExpanded by remember { mutableStateOf(false) }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.ic_mn_sliders),
                            contentDescription = null,
                            tint = MediaNestColors.Accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Max Concurrent Downloads",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MediaNestColors.TextPrimary
                        )
                    }

                    Text(
                        "Number of simultaneous background network download streams (1–5).",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = MediaNestColors.TextSecondary
                    )

                    ExposedDropdownMenuBox(
                        expanded = maxConcurrentExpanded,
                        onExpandedChange = { maxConcurrentExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = when (maxConcurrent) {
                                1 -> "1 stream (Minimal bandwidth)"
                                2 -> "2 parallel (Default)"
                                5 -> "5 parallel (Maximum)"
                                else -> "$maxConcurrent parallel streams"
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Concurrency Limit") },
                            trailingIcon = {
                                Icon(
                                    painter = painterResource(if (maxConcurrentExpanded) R.drawable.ic_mn_chevron_up else R.drawable.ic_mn_chevron_down),
                                    contentDescription = null,
                                    tint = MediaNestColors.TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(
                                    ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                    enabled = true
                                ),
                            colors = customTextFieldColors(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = maxConcurrentExpanded,
                            onDismissRequest = { maxConcurrentExpanded = false },
                            containerColor = MediaNestColors.Card
                        ) {
                            maxConcurrentOptions.forEach { option ->
                                val label = when (option) {
                                    1 -> "1 stream (Minimal bandwidth)"
                                    2 -> "2 parallel (Default)"
                                    3 -> "3 parallel streams"
                                    4 -> "4 parallel streams"
                                    5 -> "5 parallel (Maximum)"
                                    else -> "$option parallel"
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            label,
                                            color = if (option == maxConcurrent) MediaNestColors.Accent else MediaNestColors.TextPrimary
                                        )
                                    },
                                    onClick = {
                                        coroutineScope.launch {
                                            downloadPreferences.setMaxConcurrentDownloads(option)
                                        }
                                        maxConcurrentExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // 2. PREFERENCES
            // =========================================================================
            SettingsSectionHeader(
                title = "Preferences",
                iconRes = R.drawable.ic_mn_sliders,
                subtitle = "Shorts filtering, collections layout & playback rules"
            )

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    MnNoteBox(
                        title = "Display & Playback Preferences",
                        variant = NoteBoxVariant.STANDARD,
                        iconPainter = painterResource(R.drawable.ic_mn_sliders)
                    ) {
                        Text(
                            "Customize feed content filtering, default collection layouts, and video playback thresholds."
                        )
                    }

                    // Show Shorts toggle
                    val showShorts by subscriptionsPreferences.showShorts.collectAsStateWithLifecycle(
                        initialValue = SubscriptionsPreferences.DEFAULT_SHOW_SHORTS
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                coroutineScope.launch {
                                    subscriptionsPreferences.setShowShorts(!showShorts)
                                }
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_youtube),
                                    contentDescription = null,
                                    tint = MediaNestColors.Accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Show YouTube Shorts",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MediaNestColors.TextPrimary
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Include short-form videos in subscriptions and channel feeds",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = MediaNestColors.TextSecondary
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = showShorts,
                            onCheckedChange = { checked ->
                                coroutineScope.launch {
                                    subscriptionsPreferences.setShowShorts(checked)
                                }
                            },
                            colors = customSwitchColors()
                        )
                    }

                    HorizontalDivider(color = MediaNestColors.Border, thickness = 1.dp)

                    // Collections View Mode toggle
                    val collectionsViewMode by collectionsPreferences.viewMode.collectAsStateWithLifecycle(
                        initialValue = CollectionsPreferences.DEFAULT_VIEW_MODE
                    )
                    val isGrid = collectionsViewMode.equals("GRID", ignoreCase = true)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(if (isGrid) R.drawable.ic_mn_grid else R.drawable.ic_mn_list),
                                    contentDescription = null,
                                    tint = MediaNestColors.Accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Collections Layout",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MediaNestColors.TextPrimary
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Default view for media collections (${if (isGrid) "Grid" else "List"})",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = MediaNestColors.TextSecondary
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MediaNestChip(
                                label = "Grid",
                                selected = isGrid,
                                onClick = {
                                    coroutineScope.launch {
                                        collectionsPreferences.setViewMode("GRID")
                                    }
                                }
                            )
                            MediaNestChip(
                                label = "List",
                                selected = !isGrid,
                                onClick = {
                                    coroutineScope.launch {
                                        collectionsPreferences.setViewMode("LIST")
                                    }
                                }
                            )
                        }
                    }

                    HorizontalDivider(color = MediaNestColors.Border, thickness = 1.dp)

                    // Auto-mark as Watched switch
                    val autoMarkWatched by playbackPreferences.autoMarkWatched.collectAsStateWithLifecycle(
                        initialValue = PlaybackPreferences.DEFAULT_AUTO_MARK_WATCHED
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                coroutineScope.launch {
                                    playbackPreferences.setAutoMarkWatched(!autoMarkWatched)
                                }
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_watched),
                                    contentDescription = null,
                                    tint = MediaNestColors.Accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Auto-mark as Watched",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MediaNestColors.TextPrimary
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Automatically mark videos as watched when remaining playback time is ≤ 1 minute.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = MediaNestColors.TextSecondary
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = autoMarkWatched,
                            onCheckedChange = { checked ->
                                coroutineScope.launch {
                                    playbackPreferences.setAutoMarkWatched(checked)
                                }
                            },
                            colors = customSwitchColors()
                        )
                    }

                    HorizontalDivider(color = MediaNestColors.Border, thickness = 1.dp)

                    // Background Audio Playback switch
                    val backgroundPlayback by playbackPreferences.backgroundPlayback.collectAsStateWithLifecycle(
                        initialValue = PlaybackPreferences.DEFAULT_BACKGROUND_PLAYBACK
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                coroutineScope.launch {
                                    playbackPreferences.setBackgroundPlayback(!backgroundPlayback)
                                }
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_music),
                                    contentDescription = null,
                                    tint = MediaNestColors.Accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Background Audio Playback",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MediaNestColors.TextPrimary
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Continue playing audio smoothly in the background when navigating away or locking screen.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = MediaNestColors.TextSecondary
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = backgroundPlayback,
                            onCheckedChange = { checked ->
                                coroutineScope.launch {
                                    playbackPreferences.setBackgroundPlayback(checked)
                                }
                            },
                            colors = customSwitchColors()
                        )
                    }
                }
            }

            // =========================================================================
            // 3. DATA MANAGEMENT & STORAGE
            // =========================================================================
            SettingsSectionHeader(
                title = "Data Management & Storage",
                iconRes = R.drawable.ic_mn_file,
                subtitle = "Backups, library repair, broken files & statistics"
            )

            // App Statistics Quick Entry Card
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToStatistics
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MediaNestColors.AccentDeep),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_mn_chart),
                                contentDescription = null,
                                tint = MediaNestColors.Accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "App Statistics",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                ),
                                color = MediaNestColors.TextPrimary
                            )
                            Text(
                                "Storage usage meters, watch metrics & library breakdown",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = MediaNestColors.TextSecondary
                            )
                        }
                    }

                    Icon(
                        painter = painterResource(R.drawable.ic_mn_chevron_right),
                        contentDescription = "Navigate",
                        tint = MediaNestColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Backup & Restore Card
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Backup & Restore",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        ),
                        color = MediaNestColors.TextPrimary
                    )

                    Text(
                        "Export complete database records and media files into a ZIP archive, or restore from a previously exported archive.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = MediaNestColors.TextSecondary
                    )

                    MnNoteBox(
                        title = "Backup & Restore Explanations",
                        variant = NoteBoxVariant.STANDARD,
                        iconPainter = painterResource(R.drawable.ic_mn_file)
                    ) {
                        Text("• Export: Packages all database records (videos list, subscriptions, watch history & timestamps, custom folders, playlists, and preferences) into a portable ZIP archive. You can optionally include downloaded video & audio files for a full offline backup.")
                        Spacer(Modifier.height(4.dp))
                        Text("• Import: Overwrites database with imported records and re-extracts video & audio files to their designated local storage paths.")
                        Spacer(Modifier.height(4.dp))
                        Text("• Download Missing Files: Appears when files are missing on disk. Clicking it will re-queue and re-download completed files that are absent. Do not run 'Repair Library' first, as it will clear database references to those files.")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MediaNestButton(
                            text = "Export Backup",
                            onClick = { showExportDialog = true },
                            enabled = state !is ExportImportState.InProgress,
                            variant = MediaNestButtonVariant.Primary,
                            size = MediaNestButtonSize.Small,
                            modifier = Modifier.weight(1f),
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_cloud_up),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )

                        MediaNestButton(
                            text = "Import Backup",
                            onClick = { importLauncher.launch(arrayOf("application/zip")) },
                            enabled = state !is ExportImportState.InProgress,
                            variant = MediaNestButtonVariant.Secondary,
                            size = MediaNestButtonSize.Small,
                            modifier = Modifier.weight(1f),
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_cloud_down),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }

                    // Backup & Restore Progress / Feedback
                    val isBackupRestoreState = remember(state) {
                        when (val s = state) {
                            is ExportImportState.InProgress -> s.operation == "Exporting" || s.operation == "Restoring"
                            is ExportImportState.Success -> s.message.startsWith("Export complete") || s.message.startsWith("Restore complete")
                            is ExportImportState.Error -> s.message.startsWith("Export failed") || s.message.startsWith("Restore failed")
                            else -> false
                        }
                    }

                    if (isBackupRestoreState) {
                        when (val s = state) {
                            is ExportImportState.InProgress -> {
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "${s.operation}...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MediaNestColors.Accent
                                    )
                                    Text(
                                        "${(s.progress * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = MediaNestColors.Accent
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { s.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = MediaNestColors.Accent,
                                    trackColor = MediaNestColors.ProgressTrack
                                )
                            }
                            is ExportImportState.Success -> {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MediaNestColors.Raised,
                                    border = BorderStroke(1.dp, MediaNestColors.Success.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_mn_check_circle),
                                            contentDescription = null,
                                            tint = MediaNestColors.Success,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            s.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MediaNestColors.TextPrimary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { viewModel.resetState() },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_mn_close),
                                                contentDescription = "Close",
                                                tint = MediaNestColors.TextSecondary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            is ExportImportState.Error -> {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MediaNestColors.Raised,
                                    border = BorderStroke(1.dp, MediaNestColors.Destructive.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_mn_warning),
                                            contentDescription = null,
                                            tint = MediaNestColors.Destructive,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            s.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MediaNestColors.Destructive,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { viewModel.resetState() },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_mn_close),
                                                contentDescription = "Close",
                                                tint = MediaNestColors.Destructive,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            else -> {}
                        }
                    }

                    // Missing Files Redownload Callout & Button
                    val missingCount by viewModel.missingDownloadsCount.collectAsStateWithLifecycle()
                    if (missingCount > 0) {
                        Spacer(Modifier.height(4.dp))
                        MnNoteBox(
                            title = "Missing Media Files Detected",
                            variant = NoteBoxVariant.WARNING,
                            iconPainter = painterResource(R.drawable.ic_mn_warning)
                        ) {
                            Text(
                                "Found $missingCount completed video record(s) with missing files on storage disk. Click below to re-queue them for background download. Tip: Do NOT run 'Repair Library' first, as it will clear database references to missing files!"
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        MediaNestButton(
                            text = "Download Missing Files ($missingCount)",
                            onClick = { viewModel.redownloadMissingFiles() },
                            enabled = state !is ExportImportState.InProgress,
                            variant = MediaNestButtonVariant.Deep,
                            fullWidth = true,
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_download),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }

                    HorizontalDivider(color = MediaNestColors.Border, thickness = 1.dp)

                    // Auto-Backup Settings
                    Text(
                        "Auto-Backup Settings",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        ),
                        color = MediaNestColors.TextPrimary
                    )

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
                            trailingIcon = {
                                Icon(
                                    painter = painterResource(if (autoIntervalExpanded) R.drawable.ic_mn_chevron_up else R.drawable.ic_mn_chevron_down),
                                    contentDescription = null,
                                    tint = MediaNestColors.TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(
                                    ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                    enabled = true
                                ),
                            colors = customTextFieldColors(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = autoIntervalExpanded,
                            onDismissRequest = { autoIntervalExpanded = false },
                            containerColor = MediaNestColors.Card
                        ) {
                            autoIntervalOptions.forEach { option ->
                                val text = when (option) {
                                    0 -> "Disabled (Off)"
                                    168 -> "Every 7 days"
                                    else -> "Every $option hours"
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text,
                                            color = if (option == autoInterval) MediaNestColors.Accent else MediaNestColors.TextPrimary
                                        )
                                    },
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
                                        countdownText = UiUtils.formatDuration(diff / 1000)
                                    }
                                } else {
                                    countdownText = "Disabled"
                                }
                                delay(1000)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = MediaNestColors.Success,
                                    modifier = Modifier.size(8.dp)
                                ) {}
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Auto-Backup: Active",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MediaNestColors.Success
                                )
                            }
                            if (nextBackupTime != null && nextBackupTime!! > 0) {
                                Text(
                                    "Next: in $countdownText",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                    color = MediaNestColors.TextSecondary
                                )
                            }
                        }
                    }

                    // Local Backups List
                    val localBackups by viewModel.localBackups.collectAsStateWithLifecycle()
                    if (localBackups.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Local Backups Log (Max 3 retained):",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MediaNestColors.TextPrimary
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            localBackups.forEach { backup ->
                                val isFull = backup.name.startsWith("backup_full_")
                                val backupType = if (isFull) "Full Backup" else "Metadata"
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MediaNestColors.Raised,
                                    border = BorderStroke(1.dp, MediaNestColors.Border),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            painter = painterResource(if (isFull) R.drawable.ic_mn_video else R.drawable.ic_mn_file),
                                            contentDescription = null,
                                            tint = MediaNestColors.Accent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = backup.name,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp),
                                                color = MediaNestColors.TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val formattedTime = remember(backup.lastModified) {
                                                SimpleDateFormat("MMM d, yyyy, h:mm a", Locale.getDefault()).format(Date(backup.lastModified))
                                            }
                                            Text(
                                                text = "$formattedTime • $backupType • ${viewModel.formatSize(backup.sizeBytes)}",
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                                                color = MediaNestColors.TextSecondary
                                            )
                                        }
                                        MediaNestIconButton(
                                            onClick = {
                                                localBackupToRestore = backup
                                                localRestoreIncludeMedia = isFull
                                                showLocalRestoreDialog = true
                                            },
                                            size = MediaNestIconButtonSize.Small,
                                            enabled = state !is ExportImportState.InProgress,
                                            contentDescription = "Restore backup"
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_mn_history),
                                                contentDescription = null,
                                                tint = MediaNestColors.Accent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        MediaNestIconButton(
                                            onClick = {
                                                localBackupToDelete = backup
                                                showLocalDeleteDialog = true
                                            },
                                            size = MediaNestIconButtonSize.Small,
                                            enabled = state !is ExportImportState.InProgress,
                                            contentDescription = "Delete backup"
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_mn_trash),
                                                contentDescription = null,
                                                tint = MediaNestColors.Destructive,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Library Repair Card
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.ic_mn_repair),
                            contentDescription = null,
                            tint = MediaNestColors.Accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Library Repair",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MediaNestColors.TextPrimary
                        )
                    }

                    Text(
                        "Scan storage directories on disk and repair broken file references. Fixes corrupted metadata paths and eliminates orphan entries.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = MediaNestColors.TextSecondary
                    )

                    MnNoteBox(
                        title = "Library Repair Details",
                        variant = NoteBoxVariant.STANDARD,
                        iconPainter = painterResource(R.drawable.ic_mn_repair)
                    ) {
                        Text("• Scans video and audio storage directories on disk.")
                        Spacer(Modifier.height(4.dp))
                        Text("• If a video is on disk but has an incorrect path in the database, it fixes the path.")
                        Spacer(Modifier.height(4.dp))
                        Text("• If a video in the database is missing on disk, it clears its offline status.")
                        Spacer(Modifier.height(4.dp))
                        Text("• Any files on disk not linked to any database video or download are cleaned up as orphans. It does NOT search for or add new videos to your library.")
                    }

                    MediaNestButton(
                        text = "Repair Library",
                        onClick = { viewModel.repairLibrary() },
                        enabled = state !is ExportImportState.InProgress,
                        variant = MediaNestButtonVariant.Deep,
                        fullWidth = true,
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_mn_repair),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )

                    // Repair status banner
                    val isRepairState = remember(state) {
                        when (val s = state) {
                            is ExportImportState.InProgress -> s.operation == "Repairing"
                            is ExportImportState.Success -> s.message.startsWith("Repair:")
                            is ExportImportState.Error -> s.message.startsWith("Repair failed:")
                            else -> false
                        }
                    }

                    if (isRepairState) {
                        when (val s = state) {
                            is ExportImportState.InProgress -> {
                                Spacer(Modifier.height(2.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Repairing library...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MediaNestColors.Accent
                                    )
                                    Text(
                                        "${(s.progress * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = MediaNestColors.Accent
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { s.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = MediaNestColors.Accent,
                                    trackColor = MediaNestColors.ProgressTrack
                                )
                            }
                            is ExportImportState.Success -> {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MediaNestColors.Raised,
                                    border = BorderStroke(1.dp, MediaNestColors.Success.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_mn_check_circle),
                                                contentDescription = null,
                                                tint = MediaNestColors.Success,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                s.message,
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                                color = MediaNestColors.TextPrimary,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = { viewModel.resetState() },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_mn_close),
                                                    contentDescription = "Dismiss",
                                                    tint = MediaNestColors.TextSecondary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        TextButton(
                                            onClick = { showRepairDetailsDialog = true },
                                            modifier = Modifier.align(Alignment.End)
                                        ) {
                                            Text("Show Details", color = MediaNestColors.Accent, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                            is ExportImportState.Error -> {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MediaNestColors.Raised,
                                    border = BorderStroke(1.dp, MediaNestColors.Destructive.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_mn_warning),
                                            contentDescription = null,
                                            tint = MediaNestColors.Destructive,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            s.message,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                            color = MediaNestColors.Destructive,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { viewModel.resetState() },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_mn_close),
                                                contentDescription = "Dismiss",
                                                tint = MediaNestColors.Destructive,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }

            // Broken Media Cleaner Card
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.ic_mn_trash),
                            contentDescription = null,
                            tint = MediaNestColors.Accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Broken Media Cleaner",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MediaNestColors.TextPrimary
                        )
                    }

                    Text(
                        "Identify and remove incomplete downloads or residual orphaned files consuming disk space.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = MediaNestColors.TextSecondary
                    )

                    MediaNestButton(
                        text = if (isScanningOrphans) "Scanning..." else "Scan for Broken Files",
                        onClick = {
                            viewModel.scanOrphanFiles()
                            hasScannedOrphans = true
                        },
                        enabled = !isScanningOrphans,
                        variant = MediaNestButtonVariant.Deep,
                        fullWidth = true,
                        leadingIcon = {
                            if (isScanningOrphans) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = MediaNestColors.Accent,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_search),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    )

                    if (hasScannedOrphans && !isScanningOrphans) {
                        if (orphanFiles.isEmpty()) {
                            Row(
                                modifier = Modifier.padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_check_circle),
                                    contentDescription = null,
                                    tint = MediaNestColors.Success,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "No broken files found. Storage is clean!",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = MediaNestColors.Success
                                )
                            }
                        } else {
                            val totalSize = orphanFiles.sumOf { it.sizeBytes }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MediaNestColors.Raised,
                                border = BorderStroke(1.dp, MediaNestColors.Destructive.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Found ${orphanFiles.size} broken files (${viewModel.formatOrphanSize(totalSize)})",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MediaNestColors.Destructive
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    MediaNestButton(
                                        text = "View & Clean Broken Files",
                                        onClick = { showBrokenFilesDialog = true },
                                        variant = MediaNestButtonVariant.DangerSolid,
                                        size = MediaNestButtonSize.Small,
                                        fullWidth = true
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // 4. ABOUT & UPDATES
            // =========================================================================
            SettingsSectionHeader(
                title = "About & Updates",
                iconRes = R.drawable.ic_mn_info,
                subtitle = "App information, notifications & releases"
            )

            val updateState by viewModel.updateState.collectAsStateWithLifecycle()
            val currentAppVersion = remember {
                try {
                    "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})"
                } catch (_: Exception) {
                    try {
                        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                        "v${pInfo.versionName ?: "1.0.9"} (Build ${pInfo.longVersionCode})"
                    } catch (_: Exception) {
                        "v1.0.9 (Build 9)"
                    }
                }
            }

            // About MediaNest Info Card
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MnNoteBox(
                        title = "About MediaNest",
                        variant = NoteBoxVariant.STANDARD,
                        iconPainter = painterResource(R.drawable.ic_mn_info)
                    ) {
                        Text(
                            "A premium offline-first media manager and subscription player designed to organize, save, and stream your favorite content seamlessly."
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MediaNestColors.AccentDeep),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_mn_info),
                                contentDescription = null,
                                tint = MediaNestColors.Accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "MediaNest",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                ),
                                color = MediaNestColors.TextPrimary
                            )
                            Text(
                                "Version $currentAppVersion • By Kushal",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = MediaNestColors.Accent
                            )
                        }
                    }

                    HorizontalDivider(color = MediaNestColors.Border, thickness = 1.dp)

                    // In-App Notifications Hub Nav Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onNavigateToNotifications)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MediaNestColors.AccentDeep),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_bell),
                                    contentDescription = null,
                                    tint = MediaNestColors.Accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "In-App Notifications",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    ),
                                    color = MediaNestColors.TextPrimary
                                )
                                Text(
                                    "Download completions, background sync alerts & updates",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                    color = MediaNestColors.TextSecondary
                                )
                            }
                        }

                        Icon(
                            painter = painterResource(R.drawable.ic_mn_chevron_right),
                            contentDescription = "Navigate",
                            tint = MediaNestColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // App Updates Card
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.ic_mn_refresh),
                            contentDescription = null,
                            tint = MediaNestColors.Accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Software Updates",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MediaNestColors.TextPrimary
                        )
                    }

                    val autoCheckInterval by viewModel.autoCheckIntervalHours.collectAsStateWithLifecycle()
                    val autoCheckOptions = listOf(0, 24, 168)
                    var autoCheckExpanded by remember { mutableStateOf(false) }

                    ExposedDropdownMenuBox(
                        expanded = autoCheckExpanded,
                        onExpandedChange = { autoCheckExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = when (autoCheckInterval) {
                                0 -> "Off (Manual only)"
                                168 -> "Every 7 days"
                                else -> "Every 24 hours (daily)"
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Auto-check for updates") },
                            trailingIcon = {
                                Icon(
                                    painter = painterResource(if (autoCheckExpanded) R.drawable.ic_mn_chevron_up else R.drawable.ic_mn_chevron_down),
                                    contentDescription = null,
                                    tint = MediaNestColors.TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(
                                    ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                    enabled = true
                                ),
                            colors = customTextFieldColors(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = autoCheckExpanded,
                            onDismissRequest = { autoCheckExpanded = false },
                            containerColor = MediaNestColors.Card
                        ) {
                            autoCheckOptions.forEach { option ->
                                val text = when (option) {
                                    0 -> "Off (Manual only)"
                                    168 -> "Every 7 days"
                                    else -> "Every 24 hours (daily)"
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text,
                                            color = if (option == autoCheckInterval) MediaNestColors.Accent else MediaNestColors.TextPrimary
                                        )
                                    },
                                    onClick = {
                                        viewModel.setAutoCheckIntervalHours(option)
                                        autoCheckExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    val isChecking = updateState is UpdateState.Checking
                    val isBusy = updateState is UpdateState.Downloading || updateState is UpdateState.ReadyToInstall

                    MediaNestButton(
                        text = "Check for Updates",
                        onClick = { viewModel.checkForUpdates() },
                        enabled = !isChecking && !isBusy,
                        variant = MediaNestButtonVariant.Primary,
                        fullWidth = true,
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_mn_refresh),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )

                    when (val s = updateState) {
                        is UpdateState.Idle -> {
                            Text(
                                text = if (autoCheckInterval > 0) {
                                    "Updates are checked automatically, and you can check manually anytime."
                                } else {
                                    "Auto-check is off. Check manually anytime."
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = MediaNestColors.TextSecondary
                            )
                        }
                        is UpdateState.Checking -> {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MediaNestColors.Accent,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "Checking GitHub releases...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MediaNestColors.Accent
                                )
                            }
                        }
                        is UpdateState.UpdateAvailable -> {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MediaNestColors.Raised,
                                border = BorderStroke(1.dp, MediaNestColors.Accent),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        "New Version Available: v${s.latestVersion}",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MediaNestColors.Accent
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        s.changelog,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                        color = MediaNestColors.TextSecondary,
                                        maxLines = 5,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    if (BuildConfig.DEBUG) {
                                        Text(
                                            text = "Debug build active: Updates cannot be auto-installed. Please update via Android Studio / Gradle.",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = MediaNestColors.TextSecondary
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        TextButton(
                                            onClick = { viewModel.resetUpdateState() },
                                            modifier = Modifier.align(Alignment.End)
                                        ) {
                                            Text("Dismiss", color = MediaNestColors.Accent)
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            MediaNestButton(
                                                text = "Download & Install",
                                                onClick = { viewModel.downloadAndInstallUpdate(s.downloadUrl) },
                                                variant = MediaNestButtonVariant.Primary,
                                                modifier = Modifier.weight(1.5f)
                                            )
                                            MediaNestButton(
                                                text = "Cancel",
                                                onClick = { viewModel.resetUpdateState() },
                                                variant = MediaNestButtonVariant.Ghost,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        is UpdateState.NoUpdateAvailable -> {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_check_circle),
                                    contentDescription = null,
                                    tint = MediaNestColors.Success,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "You are on the latest version.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MediaNestColors.Success
                                )
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { viewModel.resetUpdateState() }) {
                                    Text("Dismiss", color = MediaNestColors.TextSecondary, fontSize = 12.sp)
                                }
                            }
                        }
                        is UpdateState.Downloading -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                val progressPercent = (s.progress * 100).toInt()
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Downloading update...", style = MaterialTheme.typography.bodySmall, color = MediaNestColors.Accent)
                                    Text("$progressPercent%", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MediaNestColors.Accent)
                                }
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { s.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = MediaNestColors.Accent,
                                    trackColor = MediaNestColors.ProgressTrack
                                )
                                Spacer(Modifier.height(8.dp))
                                TextButton(
                                    onClick = { viewModel.cancelUpdateDownload() },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Cancel Download", color = MediaNestColors.Destructive, fontSize = 12.sp)
                                }
                            }
                        }
                        is UpdateState.ReadyToInstall -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "Update downloaded and ready to install.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MediaNestColors.Success
                                )
                                Spacer(Modifier.height(8.dp))
                                MediaNestButton(
                                    text = "Install Update",
                                    onClick = { viewModel.installUpdate() },
                                    variant = MediaNestButtonVariant.Primary,
                                    fullWidth = true
                                )
                                Spacer(Modifier.height(4.dp))
                                TextButton(
                                    onClick = { viewModel.resetUpdateState() },
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                ) {
                                    Text("Dismiss", color = MediaNestColors.TextSecondary)
                                }
                            }
                        }
                        is UpdateState.Error -> {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MediaNestColors.Raised,
                                border = BorderStroke(1.dp, MediaNestColors.Destructive.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_mn_warning),
                                            contentDescription = null,
                                            tint = MediaNestColors.Destructive,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Update check failed",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MediaNestColors.Destructive
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        s.message,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                        color = MediaNestColors.TextSecondary
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    MediaNestButton(
                                        text = "Try Again",
                                        onClick = { viewModel.checkForUpdates() },
                                        variant = MediaNestButtonVariant.Deep,
                                        size = MediaNestButtonSize.Small,
                                        fullWidth = true
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // 5. VPS SYNC & CLOUD
            // =========================================================================
            SettingsSectionHeader(
                title = "VPS Sync & Cloud",
                iconRes = R.drawable.ic_mn_cloud,
                subtitle = "Cross-device synchronization server"
            )

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MnNoteBox(
                        title = "VPS Cloud Sync",
                        variant = NoteBoxVariant.STANDARD,
                        iconPainter = painterResource(R.drawable.ic_mn_cloud)
                    ) {
                        Text(
                            "Synchronize watch history, favorites, custom folders, playlists, and subscription channels across your devices using your private self-hosted VPS server instance. Media files are stored locally and not transmitted over sync."
                        )
                    }

                    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
                    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()

                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { viewModel.setServerUrl(it) },
                        label = { Text("VPS Server URL") },
                        placeholder = { Text("https://your-vps-ip:8000") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = customTextFieldColors(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { viewModel.setApiKey(it) },
                        label = { Text("API Key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = customTextFieldColors(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MediaNestButton(
                            text = "Register Device",
                            onClick = { viewModel.registerDevice(serverUrl) },
                            enabled = serverUrl.isNotBlank(),
                            variant = MediaNestButtonVariant.Deep,
                            size = MediaNestButtonSize.Small,
                            modifier = Modifier.weight(1f),
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_device),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )

                        MediaNestButton(
                            text = "Sync Now",
                            onClick = { viewModel.triggerSync() },
                            enabled = serverUrl.isNotBlank() && apiKey.isNotBlank(),
                            variant = MediaNestButtonVariant.Primary,
                            size = MediaNestButtonSize.Small,
                            modifier = Modifier.weight(1f),
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_cloud_up),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }

                    // Auto-sync interval dropdown
                    val interval by viewModel.syncIntervalHours.collectAsStateWithLifecycle()
                    val intervalOptions = listOf(0, 1, 2, 6, 12, 24)
                    var intervalExpanded by remember { mutableStateOf(false) }

                    ExposedDropdownMenuBox(
                        expanded = intervalExpanded,
                        onExpandedChange = { intervalExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = when (interval) {
                                0 -> "Manual only"
                                1 -> "Every 1 hour"
                                else -> "Every $interval hours"
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Auto-sync interval") },
                            trailingIcon = {
                                Icon(
                                    painter = painterResource(if (intervalExpanded) R.drawable.ic_mn_chevron_up else R.drawable.ic_mn_chevron_down),
                                    contentDescription = null,
                                    tint = MediaNestColors.TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(
                                    ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                    enabled = true
                                ),
                            colors = customTextFieldColors(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = intervalExpanded,
                            onDismissRequest = { intervalExpanded = false },
                            containerColor = MediaNestColors.Card
                        ) {
                            intervalOptions.forEach { option ->
                                val label = when (option) {
                                    0 -> "Manual only"
                                    1 -> "Every 1 hour"
                                    else -> "Every $option hours"
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            label,
                                            color = if (option == interval) MediaNestColors.Accent else MediaNestColors.TextPrimary
                                        )
                                    },
                                    onClick = {
                                        viewModel.setSyncIntervalHours(option)
                                        intervalExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Sync State Feedback
                    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
                    LaunchedEffect(syncState) {
                        if (syncState is SyncState.Success || syncState is SyncState.Error) {
                            delay(3000)
                            viewModel.resetSyncState()
                        }
                    }

                    when (val s = syncState) {
                        is SyncState.Syncing -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = MediaNestColors.Accent,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Syncing with VPS...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MediaNestColors.Accent
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = MediaNestColors.Accent,
                                    trackColor = MediaNestColors.ProgressTrack
                                )
                            }
                        }
                        is SyncState.Success -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_check_circle),
                                    contentDescription = null,
                                    tint = MediaNestColors.Success,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    s.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MediaNestColors.Success
                                )
                            }
                        }
                        is SyncState.Error -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_warning),
                                    contentDescription = null,
                                    tint = MediaNestColors.Destructive,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    s.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MediaNestColors.Destructive
                                )
                            }
                        }
                        else -> {}
                    }

                    // Metadata details (Last sync, Device ID with copy button)
                    val lastSyncAt by viewModel.lastSyncAt.collectAsStateWithLifecycle()
                    if (lastSyncAt > 0) {
                        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(lastSyncAt))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(R.drawable.ic_mn_history),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MediaNestColors.TextSecondary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Last synced: $date",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = MediaNestColors.TextSecondary
                            )
                        }
                    }

                    val deviceId by viewModel.deviceId.collectAsStateWithLifecycle()
                    if (deviceId.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_device),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MediaNestColors.TextSecondary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        "Device ID",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MediaNestColors.TextPrimary
                                    )
                                    Text(
                                        deviceId,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MediaNestColors.TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            MediaNestIconButton(
                                onClick = {
                                    try {
                                        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Device ID", deviceId)
                                        clipboardManager.setPrimaryClip(clip)
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Device ID copied to clipboard")
                                        }
                                    } catch (_: Exception) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Failed to copy Device ID")
                                        }
                                    }
                                },
                                size = MediaNestIconButtonSize.Small,
                                contentDescription = "Copy Device ID"
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_copy),
                                    contentDescription = "Copy",
                                    tint = MediaNestColors.Accent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // VPS Sync Activity Log Card
            var logExpanded by remember { mutableStateOf(false) }
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(R.drawable.ic_mn_history),
                                contentDescription = null,
                                tint = MediaNestColors.Accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Sync Activity Log",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MediaNestColors.TextPrimary
                            )
                        }

                        Row {
                            if (logExpanded && viewModel.syncLog.value.isNotEmpty()) {
                                TextButton(onClick = { viewModel.clearSyncLog() }) {
                                    Text("Clear", style = MaterialTheme.typography.bodySmall, color = MediaNestColors.Destructive)
                                }
                            }
                            TextButton(onClick = { logExpanded = !logExpanded }) {
                                Text(
                                    if (logExpanded) "Hide" else "Show",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MediaNestColors.Accent
                                )
                            }
                        }
                    }

                    if (logExpanded) {
                        Spacer(Modifier.height(8.dp))
                        val log by viewModel.syncLog.collectAsStateWithLifecycle()
                        if (log.isEmpty()) {
                            Text(
                                "No sync activity recorded yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MediaNestColors.TextSecondary
                            )
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                                items(log.take(50)) { entry ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            entry.formattedTime,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                                            modifier = Modifier.width(52.dp),
                                            color = MediaNestColors.TextSecondary
                                        )
                                        val iconRes = when (entry.type) {
                                            "error" -> R.drawable.ic_mn_warning
                                            "push" -> R.drawable.ic_mn_cloud_up
                                            "pull" -> R.drawable.ic_mn_cloud_down
                                            "apply" -> R.drawable.ic_mn_edit
                                            else -> R.drawable.ic_mn_info
                                        }
                                        val tint = when (entry.type) {
                                            "error" -> MediaNestColors.Destructive
                                            "apply" -> MediaNestColors.Success
                                            else -> MediaNestColors.TextSecondary
                                        }
                                        Icon(
                                            painter = painterResource(iconRes),
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = tint
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            (entry.table?.let { "[$it] " } ?: "") + entry.summary,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                            color = MediaNestColors.TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // =========================================================================
    // DIALOGS & OVERLAYS (Design 2.0 Styled)
    // =========================================================================

    if (showUnsupportedPathDialog) {
        AlertDialog(
            onDismissRequest = { showUnsupportedPathDialog = false },
            containerColor = MediaNestColors.Card,
            titleContentColor = MediaNestColors.TextPrimary,
            textContentColor = MediaNestColors.TextSecondary,
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mn_warning),
                        contentDescription = null,
                        tint = MediaNestColors.Destructive,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Unsupported Location")
                }
            },
            text = { Text(unsupportedPathMessage, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { showUnsupportedPathDialog = false }) {
                    Text("OK", color = MediaNestColors.Accent)
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
        var selectedOption by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            containerColor = MediaNestColors.Card,
            titleContentColor = MediaNestColors.TextPrimary,
            textContentColor = MediaNestColors.TextSecondary,
            shape = RoundedCornerShape(16.dp),
            title = { Text("Export Backup Options") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Choose backup contents to package:", style = MaterialTheme.typography.bodyMedium, color = MediaNestColors.TextSecondary)

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (!selectedOption) MediaNestColors.Raised else Color.Transparent,
                        border = BorderStroke(1.dp, if (!selectedOption) MediaNestColors.Accent else MediaNestColors.Border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedOption = false }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = !selectedOption,
                                onClick = { selectedOption = false },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MediaNestColors.Accent,
                                    unselectedColor = MediaNestColors.TextSecondary
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Metadata Only", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MediaNestColors.TextPrimary)
                                Text("Database, folders, and preferences (~$formattedMetadataSize)", style = MaterialTheme.typography.bodySmall, color = MediaNestColors.TextSecondary)
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedOption) MediaNestColors.Raised else Color.Transparent,
                        border = BorderStroke(1.dp, if (selectedOption) MediaNestColors.Accent else MediaNestColors.Border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedOption = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedOption,
                                onClick = { selectedOption = true },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MediaNestColors.Accent,
                                    unselectedColor = MediaNestColors.TextSecondary
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Full Backup", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MediaNestColors.TextPrimary)
                                Text("Database + all downloaded video and audio files (~$formattedFullSize)", style = MaterialTheme.typography.bodySmall, color = MediaNestColors.TextSecondary)
                            }
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
                    Text("Export", color = MediaNestColors.Accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Cancel", color = MediaNestColors.TextSecondary)
                }
            }
        )
    }

    val importInspection by viewModel.importInspection.collectAsStateWithLifecycle()
    if (importInspection is ImportInspectionState.NeedsChoice) {
        val uri = (importInspection as ImportInspectionState.NeedsChoice).uri
        var selectedOption by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { viewModel.resetImportInspection() },
            containerColor = MediaNestColors.Card,
            titleContentColor = MediaNestColors.TextPrimary,
            textContentColor = MediaNestColors.TextSecondary,
            shape = RoundedCornerShape(16.dp),
            title = { Text("Import Options") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("This archive contains media files. Choose restore mode:", style = MaterialTheme.typography.bodyMedium, color = MediaNestColors.TextSecondary)

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedOption) MediaNestColors.Raised else Color.Transparent,
                        border = BorderStroke(1.dp, if (selectedOption) MediaNestColors.Accent else MediaNestColors.Border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedOption = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedOption,
                                onClick = { selectedOption = true },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MediaNestColors.Accent,
                                    unselectedColor = MediaNestColors.TextSecondary
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Full Restore", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MediaNestColors.TextPrimary)
                                Text("Restore database, settings, and extract media files", style = MaterialTheme.typography.bodySmall, color = MediaNestColors.TextSecondary)
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (!selectedOption) MediaNestColors.Raised else Color.Transparent,
                        border = BorderStroke(1.dp, if (!selectedOption) MediaNestColors.Accent else MediaNestColors.Border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedOption = false }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = !selectedOption,
                                onClick = { selectedOption = false },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MediaNestColors.Accent,
                                    unselectedColor = MediaNestColors.TextSecondary
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Metadata Only", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MediaNestColors.TextPrimary)
                                Text("Restore database and settings, skipping media extractions", style = MaterialTheme.typography.bodySmall, color = MediaNestColors.TextSecondary)
                            }
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
                    Text("Restore", color = MediaNestColors.Accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.resetImportInspection() }) {
                    Text("Cancel", color = MediaNestColors.TextSecondary)
                }
            }
        )
    }

    if (migrationState is MigrationState.InProgress) {
        val s = migrationState as MigrationState.InProgress
        AlertDialog(
            onDismissRequest = { },
            containerColor = MediaNestColors.Card,
            titleContentColor = MediaNestColors.TextPrimary,
            textContentColor = MediaNestColors.TextSecondary,
            shape = RoundedCornerShape(16.dp),
            title = { Text("Moving Downloaded Files") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Relocating files to new storage directory. Please do not exit.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Text("Current file: ${s.currentFile}", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MediaNestColors.TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { s.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MediaNestColors.Accent,
                        trackColor = MediaNestColors.ProgressTrack
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Moved ${s.movedCount} of ${s.totalCount} files", style = MaterialTheme.typography.bodySmall, color = MediaNestColors.Accent)
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.cancelMigration() }) {
                    Text("Cancel", color = MediaNestColors.Destructive)
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
            containerColor = MediaNestColors.Card,
            titleContentColor = MediaNestColors.TextPrimary,
            textContentColor = MediaNestColors.TextSecondary,
            shape = RoundedCornerShape(16.dp),
            title = { Text("Restore Local Backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Restore library records from ${backup.name}?")
                    Text("This operation will overwrite current library database records.", style = MaterialTheme.typography.bodySmall, color = MediaNestColors.Destructive)
                    if (isFullBackup) {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { restoreMedia = !restoreMedia }
                        ) {
                            Checkbox(
                                checked = restoreMedia,
                                onCheckedChange = { restoreMedia = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MediaNestColors.Accent,
                                    checkmarkColor = MediaNestColors.OnAccent,
                                    uncheckedColor = MediaNestColors.Border
                                )
                            )
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
                    Text("Restore", color = MediaNestColors.Accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLocalRestoreDialog = false }) {
                    Text("Cancel", color = MediaNestColors.TextSecondary)
                }
            }
        )
    }

    if (showLocalDeleteDialog && localBackupToDelete != null) {
        val backup = localBackupToDelete!!
        AlertDialog(
            onDismissRequest = { showLocalDeleteDialog = false },
            containerColor = MediaNestColors.Card,
            titleContentColor = MediaNestColors.TextPrimary,
            textContentColor = MediaNestColors.TextSecondary,
            shape = RoundedCornerShape(16.dp),
            title = { Text("Delete Local Backup") },
            text = {
                Text("Are you sure you want to delete ${backup.name}? This backup file cannot be recovered.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLocalDeleteDialog = false
                        viewModel.deleteLocalBackup(backup)
                    }
                ) {
                    Text("Delete", color = MediaNestColors.Destructive, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLocalDeleteDialog = false }) {
                    Text("Cancel", color = MediaNestColors.TextSecondary)
                }
            }
        )
    }

    if (showRepairDetailsDialog) {
        val repairState = state as? ExportImportState.Success
        val details = repairState?.details ?: emptyList()
        AlertDialog(
            onDismissRequest = { showRepairDetailsDialog = false },
            containerColor = MediaNestColors.Card,
            titleContentColor = MediaNestColors.TextPrimary,
            textContentColor = MediaNestColors.TextSecondary,
            shape = RoundedCornerShape(16.dp),
            title = { Text("Library Repair Details") },
            text = {
                if (details.isEmpty()) {
                    Text("No anomalies detected. Library is fully synchronized.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(details) { detail ->
                            if (detail.isEmpty()) {
                                Spacer(Modifier.height(6.dp))
                            } else {
                                val isHeader = !detail.startsWith(" ")
                                Text(
                                    text = detail,
                                    style = if (isHeader) {
                                        MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = MediaNestColors.TextPrimary)
                                    } else {
                                        MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, color = MediaNestColors.TextSecondary)
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRepairDetailsDialog = false }) {
                    Text("Close", color = MediaNestColors.Accent)
                }
            }
        )
    }

    if (showBrokenFilesDialog) {
        AlertDialog(
            onDismissRequest = { showBrokenFilesDialog = false },
            containerColor = MediaNestColors.Card,
            titleContentColor = MediaNestColors.TextPrimary,
            textContentColor = MediaNestColors.TextSecondary,
            shape = RoundedCornerShape(16.dp),
            title = {
                val totalSize = orphanFiles.sumOf { it.sizeBytes }
                Column {
                    Text("Broken Files Found", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${orphanFiles.size} files • Total: ${viewModel.formatOrphanSize(totalSize)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MediaNestColors.Destructive
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (orphanFiles.isNotEmpty()) {
                        MediaNestButton(
                            text = "Delete All Broken Files",
                            onClick = {
                                viewModel.deleteAllOrphans()
                                showBrokenFilesDialog = false
                            },
                            variant = MediaNestButtonVariant.DangerSolid,
                            size = MediaNestButtonSize.Small,
                            fullWidth = true,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(orphanFiles) { orphan ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MediaNestColors.Raised,
                                border = BorderStroke(1.dp, MediaNestColors.Border),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = orphan.name,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                            color = MediaNestColors.TextPrimary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = "${if (orphan.isAudio) "Audio" else "Video"} • ${viewModel.formatOrphanSize(orphan.sizeBytes)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MediaNestColors.TextSecondary
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            viewModel.deleteOrphanFile(orphan)
                                            if (orphanFiles.size <= 1) {
                                                showBrokenFilesDialog = false
                                            }
                                        }
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_mn_trash),
                                            contentDescription = "Delete file",
                                            tint = MediaNestColors.Destructive,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBrokenFilesDialog = false }) {
                    Text("Close", color = MediaNestColors.Accent)
                }
            }
        )
    }
}

// =============================================================================
// Helper Composables & Color Utilities
// =============================================================================

@Composable
private fun SettingsSectionHeader(
    title: String,
    iconRes: Int,
    subtitle: String? = null,
    badge: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MediaNestColors.Raised),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MediaNestColors.Accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        letterSpacing = (-0.2).sp
                    ),
                    color = MediaNestColors.TextPrimary
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MediaNestColors.TextSecondary
                    )
                }
            }
        }
        if (badge != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MediaNestColors.Raised,
                border = BorderStroke(1.dp, MediaNestColors.Border)
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MediaNestColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun customTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MediaNestColors.Accent,
    unfocusedBorderColor = MediaNestColors.Border,
    focusedLabelColor = MediaNestColors.Accent,
    unfocusedLabelColor = MediaNestColors.TextSecondary,
    cursorColor = MediaNestColors.Accent,
    focusedTextColor = MediaNestColors.TextPrimary,
    unfocusedTextColor = MediaNestColors.TextPrimary,
    focusedContainerColor = MediaNestColors.Raised.copy(alpha = 0.35f),
    unfocusedContainerColor = MediaNestColors.Raised.copy(alpha = 0.2f),
    disabledBorderColor = MediaNestColors.Border.copy(alpha = 0.4f),
    disabledTextColor = MediaNestColors.TextSecondary.copy(alpha = 0.6f),
    disabledLabelColor = MediaNestColors.TextSecondary.copy(alpha = 0.6f),
    disabledContainerColor = MediaNestColors.Raised.copy(alpha = 0.1f)
)

@Composable
private fun customSwitchColors() = mediaNestSwitchColors()

private fun getPathFromUri(context: Context, uri: Uri): String? {
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
                            val sdCardFile = File(rootPath + "/" + relativePath)
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
