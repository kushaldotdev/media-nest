# Implementation Plan: Phase 6 — Organization (Folders + Favorites + Search)

## System / Contract Summary
- **Package**: `com.example.medianest`
- **Favorites**: Simple boolean column on `VideoEntity` — no join table needed
- **Folders**: `FolderEntity` with nullable `parentId` self-FK for nesting. `VideoFolderJoin` for many-to-many video↔folder relationship
- **Search**: `WHERE title LIKE '%query%' OR channelName LIKE '%query%'` on `VideoDao` — simple LIKE, no FTS
- **Library screen**: Replaces placeholder. 3 tabs (All Videos / Folders / Favorites) + search bar
- **DB version**: 4 currently → bump to 5 with explicit migration (NOT destructive)
- **Migration**: `ALTER TABLE videos ADD COLUMN favorite`, `CREATE TABLE folders`, `CREATE TABLE video_folder_map`
- **Folder nesting**: Single-level load (direct children only), breadcrumb navigation for depth
- **No delete confirmation yet**: Folder delete cascades `VideoFolderJoin` rows, NOT videos

---

## Phase Order

1. **6.1** — Create `FolderEntity` + `VideoFolderJoin` entities. Bump DB v4→v5 with migration
2. **6.2** — Add `favorite` to `VideoEntity`. Add search + filter queries to `VideoDao`
3. **6.3** — Create `FolderDao` + `VideoFolderDao`
4. **6.4** — Create `LibraryViewModel` — search, folder nav, favorite toggle, video listing
5. **6.5** — Replace `LibraryScreen` — 3 tabs, search bar, video grid, folder list, favorite toggle
6. **6.6** — Create `FolderDetailScreen` + `FolderViewModel` — contents, create/rename/delete
7. **6.7** — Add favorite toggle UI to `VideoDetailScreen` + `HomeScreen`
8. **6.8** — Wire folder navigation in `AppNavigation.kt`
9. **6.9** — Build and verify

---

## Steps

### Step 6.1: Create FolderEntity + VideoFolderJoin + DB v4→v5 migration

**What**: Create two new Room entities for folder organization. Bump DB to version 5 with explicit migration.

**Where**:
- `app/src/main/java/com/example/medianest/data/local/entity/FolderEntity.kt` (new)
- `app/src/main/java/com/example/medianest/data/local/entity/VideoFolderJoin.kt` (new)
- `app/src/main/java/com/example/medianest/data/local/AppDatabase.kt`
- `app/src/main/java/com/example/medianest/di/DatabaseModule.kt`

**How**:

`FolderEntity.kt`:
```kotlin
package com.example.medianest.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "folders",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("parentId")]
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
```

`VideoFolderJoin.kt`:
```kotlin
package com.example.medianest.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "video_folder_join",
    primaryKeys = ["videoId", "folderId"],
    foreignKeys = [
        ForeignKey(
            entity = VideoEntity::class,
            parentColumns = ["id"],
            childColumns = ["videoId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("videoId"), Index("folderId")]
)
data class VideoFolderJoin(
    val videoId: String,
    val folderId: Long,
    val addedAt: Long = System.currentTimeMillis()
)
```

`AppDatabase.kt`:
- Add `FolderEntity::class` and `VideoFolderJoin::class` to `entities` array
- Change `version = 4` → `version = 5`
- Add `abstract fun folderDao(): FolderDao` and `abstract fun videoFolderDao(): VideoFolderDao`

`DatabaseModule.kt` — add `MIGRATION_4_5`:
```kotlin
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE videos ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS folders (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    parentId INTEGER REFERENCES folders(id) ON DELETE SET NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS index_folders_parentId ON folders(parentId)")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS video_folder_join (
                    videoId TEXT NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
                    folderId INTEGER NOT NULL REFERENCES folders(id) ON DELETE CASCADE,
                    addedAt INTEGER NOT NULL,
                    PRIMARY KEY (videoId, folderId)
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS index_vfj_videoId ON video_folder_join(videoId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_vfj_folderId ON video_folder_join(folderId)")
        }
    }
```

Add to `provideAppDatabase`:
```kotlin
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "media_nest.db"
        ).addMigrations(MIGRATION_3_4)
            .addMigrations(MIGRATION_4_5)
            .fallbackToDestructiveMigration(false)
            .build()
```

**Why**: Folders need a dedicated entity with self-referencing parentId for nesting. VideoFolderJoin enables many-to-many (video in multiple folders). Explicit migration preserves all Phase 4+5 data.

**Edge cases**:
- Root folder (parentId = null) — top-level folder
- Deleting a folder with children → parentId set to null, children become root folders
- Folder with videos → cascade deletes VideoFolderJoin but NOT the videos themselves
- v4 DB upgrades → all existing videos get `favorite = 0`

**Pitfalls / do not**:
- Do NOT use `fallbackToDestructiveMigration()` — would delete all data
- Do NOT forget indices on foreign key columns — Room requires them for FK entities
- Do NOT make `FolderEntity` reference `VideoEntity` — folders are independent

**Validation**: Existing app upgrades to v5 without data loss. Fresh install creates v5 schema.

---

### Step 6.2: Add favorite to VideoEntity + search/filter queries to VideoDao

**What**: Add `favorite: Boolean = false` to VideoEntity. Add search, sort, and filter DAO queries.

**Where**:
- `app/src/main/java/com/example/medianest/data/local/entity/VideoEntity.kt`
- `app/src/main/java/com/example/medianest/data/local/dao/VideoDao.kt`

**How**:

`VideoEntity.kt` — add field:
```kotlin
    val favorite: Boolean = false,
```

`VideoDao.kt` — add queries:
```kotlin
    @Query("SELECT * FROM videos WHERE title LIKE '%' || :query || '%' OR channelName LIKE '%' || :query || '%' ORDER BY addedAt DESC")
    fun searchVideos(query: String): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE favorite = 1 ORDER BY addedAt DESC")
    fun getFavoriteVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos ORDER BY addedAt DESC")
    fun getAllVideosSortedByDate(): Flow<List<VideoEntity>>

    @Query("UPDATE videos SET favorite = :favorite WHERE id = :videoId")
    suspend fun setFavorite(videoId: String, favorite: Boolean)
```

**Why**: `LIKE` search is simple and works offline. Favorites column avoids a join table. `setFavorite` query avoids loading full entity just to toggle.

**Edge cases**:
- Empty search query → return all videos (handle in ViewModel — only call search if query.isNotEmpty())
- Search with special SQL characters → SQLite LIKE escapes most chars, but `%` and `_` in query could match unexpectedly. Acceptable for now
- Favorite toggle concurrent access → single `suspend` query, no race

**Pitfalls / do not**:
- Do NOT add FTS4/FTS5 virtual table — overkill for Phase 6
- Do NOT make `favorite` nullable — use boolean with default false

**Validation**: `getFavoriteVideos()` returns only favorited videos. `searchVideos("test")` returns matches.

---

### Step 6.3: Create FolderDao + VideoFolderDao

**What**: DAO for folder CRUD and video-folder relationship queries.

**Where**:
- `app/src/main/java/com/example/medianest/data/local/dao/FolderDao.kt` (new)
- `app/src/main/java/com/example/medianest/data/local/dao/VideoFolderDao.kt` (new)
- `app/src/main/java/com/example/medianest/di/DatabaseModule.kt` — wire DAO providers

**How**:

`FolderDao.kt`:
```kotlin
package com.example.medianest.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.medianest.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE parentId IS NULL ORDER BY name ASC")
    fun getRootFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE parentId = :parentId ORDER BY name ASC")
    fun getChildFolders(parentId: Long): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getFolderById(id: Long): FolderEntity?

    @Query("SELECT * FROM folders ORDER BY name ASC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: FolderEntity): Long

    @Update
    suspend fun update(folder: FolderEntity)

    @Delete
    suspend fun delete(folder: FolderEntity)

    @Query("UPDATE folders SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: Long, name: String, updatedAt: Long = System.currentTimeMillis())
}
```

`VideoFolderDao.kt`:
```kotlin
package com.example.medianest.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Query("DELETE FROM video_folder_join WHERE videoId = :videoId AND folderId = :folderId")
    suspend fun removeVideoFromFolder(videoId: String, folderId: Long)

    @Query("SELECT folderId FROM video_folder_join WHERE videoId = :videoId")
    suspend fun getFolderIdsForVideo(videoId: String): List<Long>

    @Query("SELECT f.* FROM folders f INNER JOIN video_folder_join vfj ON f.id = vfj.folderId WHERE vfj.videoId = :videoId")
    fun getFoldersForVideo(videoId: String): Flow<List<com.example.medianest.data.local.entity.FolderEntity>>
}
```

`DatabaseModule.kt` — add providers:
```kotlin
    @Provides
    fun provideFolderDao(database: AppDatabase): FolderDao = database.folderDao()

    @Provides
    fun provideVideoFolderDao(database: AppDatabase): VideoFolderDao = database.videoFolderDao()
```

**Why**: Separate DAOs for single responsibility. `VideoFolderDao` uses JOIN queries to get videos in a folder efficiently.

**Edge cases**:
- Empty folder → Flow emits empty list
- Add video already in folder → `IGNORE` silently no-ops
- Delete folder → cascade deletes joins but not videos

**Pitfalls / do not**:
- Do NOT use `REPLACE` for `addVideoToFolder` — `IGNORE` prevents accidental overwrite of `addedAt`
- Do NOT expose raw join entities to UI — use `getVideosInFolder` which returns `VideoEntity`

**Validation**: Create folder → add videos → query returns them.

---

### Step 6.4: Create LibraryViewModel

**What**: ViewModel for LibraryScreen — manages search state, tab selection, folder navigation, favorite toggle.

**Where**:
- `app/src/main/java/com/example/medianest/ui/viewmodel/LibraryViewModel.kt` (new)

**How**:

```kotlin
package com.example.medianest.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medianest.data.local.dao.FolderDao
import com.example.medianest.data.local.dao.VideoDao
import com.example.medianest.data.local.dao.VideoFolderDao
import com.example.medianest.data.local.entity.FolderEntity
import com.example.medianest.data.local.entity.VideoEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryTab { ALL, FOLDERS, FAVORITES }

data class LibraryUiState(
    val searchQuery: String = "",
    val currentTab: LibraryTab = LibraryTab.ALL,
    val selectedFolder: FolderEntity? = null
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val videoDao: VideoDao,
    private val folderDao: FolderDao,
    private val videoFolderDao: VideoFolderDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState

    private val _searchQuery = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val videos: StateFlow<List<VideoEntity>> = _searchQuery.flatMapLatest { query ->
        if (query.isBlank()) {
            videoDao.getAllVideosSortedByDate()
        } else {
            videoDao.searchVideos(query)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val favoriteVideos: StateFlow<List<VideoEntity>> = _uiState.flatMapLatest { state ->
        if (state.currentTab == LibraryTab.FAVORITES) {
            videoDao.getFavoriteVideos()
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val folderVideos: StateFlow<List<VideoEntity>> = _uiState.flatMapLatest { state ->
        val folder = state.selectedFolder
        if (folder != null) {
            videoFolderDao.getVideosInFolder(folder.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rootFolders: StateFlow<List<FolderEntity>> = folderDao.getRootFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun setTab(tab: LibraryTab) {
        _uiState.value = _uiState.value.copy(currentTab = tab, selectedFolder = null)
    }

    fun selectFolder(folder: FolderEntity) {
        _uiState.value = _uiState.value.copy(currentTab = LibraryTab.FOLDERS, selectedFolder = folder)
    }

    fun navigateBackFromFolder() {
        _uiState.value = _uiState.value.copy(selectedFolder = null)
    }

    fun toggleFavorite(videoId: String, current: Boolean) {
        viewModelScope.launch {
            videoDao.setFavorite(videoId, !current)
        }
    }

    fun createFolder(name: String, parentId: Long? = null) {
        viewModelScope.launch {
            folderDao.insert(FolderEntity(name = name, parentId = parentId))
        }
    }

    fun deleteFolder(folder: FolderEntity) {
        viewModelScope.launch {
            folderDao.delete(folder)
            if (_uiState.value.selectedFolder?.id == folder.id) {
                _uiState.value = _uiState.value.copy(selectedFolder = null)
            }
        }
    }

    fun renameFolder(id: Long, name: String) {
        viewModelScope.launch {
            folderDao.rename(id, name)
        }
    }

    fun addVideoToFolder(videoId: String, folderId: Long) {
        viewModelScope.launch {
            videoFolderDao.addVideoToFolder(
                com.example.medianest.data.local.entity.VideoFolderJoin(
                    videoId = videoId, folderId = folderId
                )
            )
        }
    }

    fun removeVideoFromFolder(videoId: String, folderId: Long) {
        viewModelScope.launch {
            videoFolderDao.removeVideoFromFolder(videoId, folderId)
        }
    }
}
```

**Why**: Single ViewModel for Library screen with all 3 tabs. `flatMapLatest` reactively switches data sources based on UI state.

**Edge cases**:
- Typing search query → `debounce` not needed for simple LIKE queries
- Tab switch while folder selected → clean folder selection
- Empty search → shows all videos

**Pitfalls / do not**:
- Do NOT put `debounce` on search — simple LIKE is fast enough
- Do NOT expose DAO directly to Compose — use StateFlow

**Validation**: Search, tab switch, folder selection all update list reactively.

---

### Step 6.5: Replace LibraryScreen

**What**: Full Library screen with 3 tabs, search bar, video grid, folder list, favorite toggle.

**Where**:
- `app/src/main/java/com/example/medianest/ui/screens/LibraryScreen.kt`

**How**: Replace entire file:

```kotlin
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
    onFolderClick: (FolderEntity) -> Unit,
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
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Text("Clear")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Search bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search videos...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Tabs
            val tabs = listOf(LibraryTab.ALL, LibraryTab.FOLDERS, LibraryTab.FAVORITES)
            val tabLabels = listOf("All", "Folders", "Favorites")
            TabRow(selectedTabIndex = tabs.indexOf(uiState.currentTab)) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = uiState.currentTab == tab,
                        onClick = { viewModel.setTab(tab) },
                        text = { Text(tabLabels[index]) }
                    )
                }
            }

            // Content
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
                        if (video.favorite) Icons.Default.Favorite else Icons.Default.Favorite,
                        contentDescription = "Favorite",
                        tint = if (video.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
        // Breadcrumb / header
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
            // Show folder grid
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
            // Show videos in folder
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
        androidx.compose.material3.AlertDialog(
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
                androidx.compose.material3.TextButton(
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
                androidx.compose.material3.TextButton(onClick = { showCreateDialog = false; newFolderName = "" }) {
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
private fun TextButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) { content() }
}

@Composable
private fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
```

**Note**: Imports may need adjustment — `AlertDialog`, `TextButton` are in material3. The `TextButton` composable at the bottom is a helper to avoid import conflicts. Keep `import androidx.compose.material3.TextButton` and remove the private helper.

**Why**: Grid layout for videos (similar to YouTube), list layout for folders. Search bar at top for quick filtering.

**Edge cases**:
- Empty search → shows all videos
- No favorites → empty state message
- No folders → empty state with create button
- Folder with no videos → "Folder is empty"

**Pitfalls / do not**:
- Do NOT show streaming-only items — Library shows only VideoEntity entries (those persisted from previous extracts)
- Do NOT navigate to player directly from Library — use `onVideoClick` to go to `VideoDetailScreen`

**Validation**: Library shows videos with thumbnails, search filters results, favorites tab works.

---

### Step 6.6: Create FolderDetailScreen + FolderViewModel

**What**: Screen for viewing folder contents, renaming, deleting. Could be integrated into LibraryScreen instead of a separate route. Decision: skip separate screen — use LibraryScreen's folder drill-in (selectedFolder state) to show folder contents inline. This avoids adding new routes.

**Decision**: Folder contents shown inline in LibraryScreen via `selectedFolder` state. No separate `FolderDetailScreen` or `FolderViewModel` needed. `LibraryViewModel` handles everything.

---

### Step 6.7: Add favorite toggle to VideoDetailScreen + HomeScreen

**What**: Add favorite button to VideoDetailScreen and video cards in HomeScreen.

**Where**:
- `app/src/main/java/com/example/medianest/ui/screens/VideoDetailScreen.kt`
- `app/src/main/java/com/example/medianest/ui/screens/HomeScreen.kt`
- `app/src/main/java/com/example/medianest/ui/viewmodel/HomeViewModel.kt` (if it doesn't already have VideoDao access)

**How**:

`VideoDetailScreen.kt` — add favorite icon button in the top bar or near title:

Add `onToggleFavorite` parameter:
```kotlin
@Composable
fun VideoDetailScreen(
    videoInfo: ExtractedVideoInfo,
    onPlay: (StreamSource) -> Unit,
    onDownload: (StreamSource) -> Unit,
    onToggleFavorite: (Boolean) -> Unit,
    isFavorite: Boolean,
    onBack: () -> Unit
)
```

Add IconToggleButton in top bar:
```kotlin
actions = {
    IconToggleButton(
        checked = isFavorite,
        onCheckedChange = { onToggleFavorite(it) }
    ) {
        Icon(
            Icons.Default.Favorite,
            contentDescription = "Favorite",
            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

`AppNavigation.kt` — update `VideoDetailScreen` call to pass favorite state. Need VideoDao in the navigation scope to load/save favorite. Simplest: pass from the cached ExtractedVideoInfo (but it doesn't have favorite). Better: provide via `VideoDetailViewModel` which already exists.

Update `VideoDetailViewModel.kt` — add:
```kotlin
    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite

    private val videoDao: VideoDao (inject)

    fun loadFavorite(videoId: String) {
        viewModelScope.launch {
            val video = videoDao.getVideoById(videoId)
            _isFavorite.value = video?.favorite ?: false
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val newValue = !_isFavorite.value
            videoDao.setFavorite(currentVideoId, newValue)
            _isFavorite.value = newValue
        }
    }
```

Wire in `AppNavigation`:
```kotlin
VideoDetailScreen(
    ...
    onToggleFavorite = { detailViewModel.toggleFavorite() },
    isFavorite = detailViewModel.isFavorite.collectAsState().value
)
```

`HomeScreen.kt` — add favorite icon to `VideoResultCard`:
```kotlin
IconToggleButton(
    checked = /* from cache or DB */,
    onCheckedChange = { viewModel.toggleFavorite(videoId, it) }
) {
    Icon(Icons.Default.Favorite, ...)
}
```

Add `toggleFavorite` to `HomeViewModel`:
```kotlin
    fun toggleFavorite(videoId: String, favorite: Boolean) {
        viewModelScope.launch {
            videoDao.setFavorite(videoId, favorite)
        }
    }
```

**Why**: Users need to favorite from anywhere they see a video — Home, Detail, and Library.

**Edge cases**:
- Video not in DB yet → favorite button hidden or disabled
- Toggle while offline → still works (Room is local)

**Pitfalls / do not**:
- Do NOT use `lastResultCache` for favorite state — it's not persisted
- Do NOT make favorite toggle require network

**Validation**: Toggle favorite on DetailScreen → shows in Library favorites tab.

---

### Step 6.8: Wire folder navigation in AppNavigation

**What**: Update Library route to pass navigation callbacks. Wire folder items to navigate.

**Where**:
- `app/src/main/java/com/example/medianest/ui/navigation/AppNavigation.kt`

**How**:

Replace:
```kotlin
composable(BottomNavItem.Library.route) { LibraryScreen() }
```

With:
```kotlin
composable(BottomNavItem.Library.route) {
    LibraryScreen(
        onVideoClick = { videoId ->
            navController.navigate("videoDetail/$videoId")
        },
        onFolderClick = { folder ->
            // handled internally by LibraryViewModel selectedFolder state
        }
    )
}
```

**Why**: `onFolderClick` is handled inline via LibraryViewModel's `selectFolder()` — no separate route needed. `onVideoClick` navigates to existing `videoDetail/{videoId}` route.

**Edge cases**: Clicking a video in library navigates to detail, which needs cached ExtractedVideoInfo. If cache is empty, detail screen shows error. For library-originated navigation, the video may not be in cache. Fallback: `VideoDetailScreen` should handle null cache gracefully (show error or load from DB). This is a pre-existing issue — defer to future phase.

---

### Step 6.9: Build and verify

**What**: Compile and verify build succeeds.

**How**:
```bash
./gradlew :app:assembleDebug
```

**Validation**: BUILD SUCCESSFUL.

---

## Beginner Implementation Guide (execution order)

1. Create `FolderEntity.kt` + `VideoFolderJoin.kt`
2. Update `AppDatabase.kt` — add entities, version 5, abstract DAO methods
3. Create `MIGRATION_4_5` in `DatabaseModule.kt`, wire into builder
4. Add `favorite` field to `VideoEntity.kt`
5. Add search/filter queries to `VideoDao.kt`
6. Create `FolderDao.kt`
7. Create `VideoFolderDao.kt`
8. Add DAO providers in `DatabaseModule.kt`
9. Create `LibraryViewModel.kt`
10. Replace `LibraryScreen.kt`
11. Update `VideoDetailViewModel.kt` — add favorite toggle
12. Update `VideoDetailScreen.kt` — add favorite icon
13. Update `HomeViewModel.kt` — add toggleFavorite
14. Update `HomeScreen.kt` — add favorite icon on video cards
15. Update `AppNavigation.kt` — wire LibraryScreen callbacks
16. Build `./gradlew :app:assembleDebug`

---

## Final Verification Checklist

- [ ] `./gradlew :app:assembleDebug` succeeds
- [ ] App with v4 DB upgrades to v5 without data loss
- [ ] Fresh install creates v5 schema
- [ ] Library shows all persisted videos in grid
- [ ] Search bar filters videos by title/channel
- [ ] Favorites tab shows only favorited videos
- [ ] Toggle favorite on VideoDetailScreen → reflected in Library
- [ ] Toggle favorite on HomeScreen card → reflected in Library
- [ ] Folders tab shows root folders
- [ ] Create folder dialog works
- [ ] Tap folder → shows videos in that folder
- [ ] Back from folder → shows root folders again
- [ ] Delete folder → removes from list, videos NOT deleted
- [ ] Empty states shown for no videos, no favorites, empty folder
- [ ] Video count not shown (deferred, low priority)

---

## Stop Conditions

- `MIGRATION_4_5` fails → verify v4 schema columns match exactly (videos table has `localFilePath` column from Phase 5)
- `FolderEntity` FK on `parentId` fails → verify `FolderEntity` class is registered in `entities` array before indices
- `VideoFolderJoin` composite PK fails → verify `primaryKeys` = `["videoId", "folderId"]` syntax matches Room 2.7 conventions
- `flatMapLatest` in ViewModel fails to compile → verify `@OptIn(ExperimentalCoroutinesApi::class)` is present
- Library doesn't show videos → verify `VideoEntity` rows exist in DB (videos are only persisted on extraction or download)
- `IconToggleButton` not found → verify `material-icons-extended` dependency in build.gradle.kts (already present)
- Favorite icon on `VideoResultCard` in HomeScreen needs video lookup → HomeViewModel needs `VideoDao` injection (add it)
