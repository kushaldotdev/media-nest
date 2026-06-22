package com.example.medianest.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.medianest.data.local.entity.FolderEntity
import com.example.medianest.data.local.entity.VideoEntity
import com.example.medianest.ui.viewmodel.LibraryTab
import com.example.medianest.ui.viewmodel.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onVideoClick: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val favoriteVideos by viewModel.favoriteVideos.collectAsStateWithLifecycle()
    val folderVideos by viewModel.folderVideos.collectAsStateWithLifecycle()
    val rootFolders by viewModel.rootFolders.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        TextButton(onClick = { viewModel.setSearchQuery("") }) {
                            Text("Clear")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search videos...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

            val tabs = listOf(LibraryTab.ALL, LibraryTab.FOLDERS, LibraryTab.FAVORITES)
            val tabLabels = listOf("All", "Folders", "Favorites")
            PrimaryTabRow(selectedTabIndex = tabs.indexOf(uiState.currentTab)) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = uiState.currentTab == tab,
                        onClick = { viewModel.setTab(tab) },
                        text = { Text(tabLabels[index]) }
                    )
                }
            }

            when (uiState.currentTab) {
                LibraryTab.ALL -> {
                    if (videos.isEmpty()) {
                        EmptyState("No videos in library")
                    } else {
                        VideoGrid(videos = videos, onVideoClick = onVideoClick, onFavoriteToggle = { video ->
                            viewModel.toggleFavorite(video.id, video.favorite)
                        })
                    }
                }
                LibraryTab.FOLDERS -> {
                    FolderContent(
                        folders = rootFolders,
                        folderVideos = folderVideos,
                        selectedFolder = uiState.selectedFolder,
                        onFolderClick = { viewModel.selectFolder(it) },
                        onCreateFolder = { viewModel.createFolder(it) },
                        onDeleteFolder = { viewModel.deleteFolder(it) },
                        onNavigateBack = { viewModel.navigateBackFromFolder() },
                        onVideoClick = onVideoClick,
                        onFavoriteToggle = { video ->
                            viewModel.toggleFavorite(video.id, video.favorite)
                        }
                    )
                }
                LibraryTab.FAVORITES -> {
                    if (favoriteVideos.isEmpty()) {
                        EmptyState("No favorite videos")
                    } else {
                        VideoGrid(videos = favoriteVideos, onVideoClick = onVideoClick, onFavoriteToggle = { video ->
                            viewModel.toggleFavorite(video.id, video.favorite)
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoGrid(
    videos: List<VideoEntity>,
    onVideoClick: (String) -> Unit,
    onFavoriteToggle: (VideoEntity) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        modifier = Modifier.fillMaxSize().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(videos, key = { it.id }) { video ->
            VideoCard(video = video, onClick = { onVideoClick(video.id) }, onFavoriteToggle = { onFavoriteToggle(video) })
        }
    }
}

@Composable
private fun VideoCard(
    video: VideoEntity,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                modifier = Modifier.fillMaxWidth().height(100.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(video.title, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    Text(video.channelName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconToggleButton(
                    checked = video.favorite,
                    onCheckedChange = { onFavoriteToggle() }
                ) {
                    Icon(
                        if (video.favorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (video.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { }) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add to playlist")
                }
            }
        }
    }
}

@Composable
private fun FolderContent(
    folders: List<FolderEntity>,
    folderVideos: List<VideoEntity>,
    selectedFolder: FolderEntity?,
    onFolderClick: (FolderEntity) -> Unit,
    onCreateFolder: (String) -> Unit,
    onDeleteFolder: (FolderEntity) -> Unit,
    onNavigateBack: () -> Unit,
    onVideoClick: (String) -> Unit,
    onFavoriteToggle: (VideoEntity) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectedFolder != null) {
                TextButton(onClick = onNavigateBack) { Text("< All folders") }
                Text(selectedFolder.name, style = MaterialTheme.typography.titleMedium)
            } else {
                Text("Folders", style = MaterialTheme.typography.titleMedium)
            }
            IconButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = "Create folder")
            }
        }

        if (selectedFolder == null) {
            if (folders.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No folders yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(folders, key = { it.id }) { folder ->
                        FolderRow(folder = folder, onClick = { onFolderClick(folder) }, onDelete = { onDeleteFolder(folder) })
                    }
                }
            }
        } else {
            if (folderVideos.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Folder is empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(folderVideos, key = { it.id }) { video ->
                        VideoCard(video = video, onClick = { onVideoClick(video.id) }, onFavoriteToggle = { onFavoriteToggle(video) })
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false; newFolderName = "" },
            title = { Text("New Folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    placeholder = { Text("Folder name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            onCreateFolder(newFolderName.trim())
                            showCreateDialog = false
                            newFolderName = ""
                        }
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false; newFolderName = "" }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun FolderRow(folder: FolderEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(8.dp))
            Text(folder.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.FolderOpen, contentDescription = "Delete folder")
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
