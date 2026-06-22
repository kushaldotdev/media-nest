# Implementation Plan: Phase 1 — Core Dependencies & Project Scaffold

## System / Contract Summary
- **Package**: `com.example.medianest` (keep)
- **Min SDK**: 29 · **Target SDK**: 36 · **Compile SDK**: `release(36)`
- **Language**: Kotlin 2.2.10 · **AGP**: 9.2.1 · **Compose BOM**: 2026.02.01
- **Architecture**: Single-module Android app, offline-first
- **DI**: Hilt (KSP-based, no kapt)
- **DB**: Room (KSP annotation processing)
- **Navigation**: Jetpack Navigation Compose with bottom nav bar

## Phase Order
1. **1.1 — Add KSP plugin + new versions to catalog**
2. **1.2 — Update root + app build.gradle.kts**
3. **1.3 — Create Application class (Hilt)**
4. **1.4 — Create Room database scaffolding**
5. **1.5 — Create Hilt DI modules**
6. **1.6 — Create navigation scaffold + placeholder screens**
7. **1.7 — Update AndroidManifest + MainActivity**

---

## Steps

### Step 1.1: Add KSP plugin + all versions to `libs.versions.toml`

**What**: Add KSP plugin, plus all library versions (Hilt, Room, Navigation, Coil, Timber, WorkManager, DataStore, kotlinx-serialization, Media3) to the version catalog.

**Where**: `gradle/libs.versions.toml`

**How**: Insert after `kotlin = "2.2.10"` in `[versions]`:
```toml
ksp = "2.2.10-1.0.33"
hilt = "2.55"
hiltNavigationCompose = "1.2.0"
room = "2.7.1"
navigationCompose = "2.8.9"
coil = "2.7.0"
timber = "5.0.1"
workManager = "2.10.0"
datastorePreferences = "1.1.4"
kotlinxSerializationJson = "1.8.1"
media3 = "1.6.1"
```

Add in `[libraries]`:
```toml
androidx-hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
androidx-hilt-compiler = { module = "com.google.dagger:hilt-compiler", version.ref = "hilt" }
androidx-hilt-navigation-compose = { module = "androidx.hilt:hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
androidx-room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigationCompose" }
io-coil-compose = { module = "io.coil-kt:coil-compose", version.ref = "coil" }
com-jakewharton-timber = { module = "com.jakewharton.timber:timber", version.ref = "timber" }
androidx-work-runtime-ktx = { module = "androidx.work:work-runtime-ktx", version.ref = "workManager" }
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastorePreferences" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinxSerializationJson" }
androidx-media3-exoplayer = { module = "androidx.media3:media3-exoplayer", version.ref = "media3" }
androidx-media3-ui = { module = "androidx.media3:media3-ui", version.ref = "media3" }
androidx-media3-session = { module = "androidx.media3:media3-session", version.ref = "media3" }
```

Add in `[plugins]`:
```toml
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
com-google-devtools-ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
com-google-dagger-hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

**Why**: All dependencies declared once in catalog; single source of truth for versions.

**Edge cases**: KSP version must match Kotlin minor version exactly (`2.2.10-1.0.33`). If wrong → build fails with `ClassNotFoundException` in KSP task.

**Pitfalls / do not**: Do not use kapt — Hilt 2.55+ supports KSP natively. Do not add `kotlin-kapt` plugin.

**Validation**: `./gradlew :app:dependencies` should resolve after Step 1.2.

**Docs**: None needed; version catalog is self-documenting.

---

### Step 1.2: Update root and app `build.gradle.kts`

**What**: Add plugins (kotlin-serialization, ksp, hilt) to root; add same + all library deps to app module.

**Where**: `build.gradle.kts` (root) + `app/build.gradle.kts`

**How**:

Root `build.gradle.kts` — append to plugins block:
```kotlin
alias(libs.plugins.kotlin.serialization) apply false
alias(libs.plugins.com.google.devtools.ksp) apply false
alias(libs.plugins.com.google.dagger.hilt) apply false
```

App `build.gradle.kts` — add to plugins block:
```kotlin
alias(libs.plugins.kotlin.serialization)
alias(libs.plugins.com.google.devtools.ksp)
alias(libs.plugins.com.google.dagger.hilt)
```

App `build.gradle.kts` — add to `android` block (inside `defaultConfig`, not inside `buildTypes`):
```kotlin
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
```

App `build.gradle.kts` — add to `dependencies` block:
```kotlin
// Hilt
implementation(libs.androidx.hilt.android)
ksp(libs.androidx.hilt.compiler)
implementation(libs.androidx.hilt.navigation.compose)

// Room
implementation(libs.androidx.room.runtime)
implementation(libs.androidx.room.ktx)
ksp(libs.androidx.room.compiler)

// Navigation
implementation(libs.androidx.navigation.compose)

// Coil
implementation(libs.io.coil.compose)

// Timber
implementation(libs.com.jakewharton.timber)

// WorkManager
implementation(libs.androidx.work.runtime.ktx)

// DataStore
implementation(libs.androidx.datastore.preferences)

// Kotlinx Serialization
implementation(libs.kotlinx.serialization.json)

// Media3 / ExoPlayer
implementation(libs.androidx.media3.exoplayer)
implementation(libs.androidx.media3.ui)
implementation(libs.androidx.media3.session)

// Lifecycle (add runtime-compose for collectAsStateWithLifecycle)
implementation(libs.androidx.lifecycle.runtime.ktx)
```

**Why**: Plugins must be declared in root with `apply false`, then applied in app module.

**Edge cases**: Order of plugin application doesn't matter for these. KSP block with `room.schemaLocation` is required for Room migration support.

**Pitfalls / do not**: Do not add `hilt-compiler` to `implementation` — it must be `ksp()`. Do not forget the `ksp` config block. Do not forget `id("org.jetbrains.kotlin.plugin.compose")` — it's already present.

**Validation**: `./gradlew :app:assembleDebug --dry-run` should complete without errors. Full build attempted after all steps.

**Docs**: None.

---

### Step 1.3: Create `MediaNestApp.kt`

**What**: Create Application class annotated with `@HiltAndroidApp`.

**Where**: `app/src/main/java/com/example/medianest/MediaNestApp.kt`

**How**:
```kotlin
package com.example.medianest

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class MediaNestApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
```

**Why**: Required by Hilt for dependency injection. Timber planted here so logging is available app-wide from the start.

**Edge cases**: `BuildConfig.DEBUG` may need `buildConfig = true` in `buildFeatures` of `app/build.gradle.kts` (check Step 1.2 — already in file as `compose = true`; add `buildConfig = true`).

**Pitfalls / do not**: Do not forget to add `android:name=".MediaNestApp"` to AndroidManifest (Step 1.7).

**Validation**: Compiles if Hilt dependencies resolve.

**Docs**: None.

---

### Step 1.4: Create Room database scaffolding

**What**: Create `AppDatabase.kt`, 4 entity classes, 4 DAO interfaces.

**Where**:
- Database: `app/src/main/java/com/example/medianest/data/local/AppDatabase.kt`
- Entities: `data/local/entity/VideoEntity.kt`, `DownloadEntity.kt`, `HistoryEntity.kt`, `PlaylistEntity.kt`
- DAOs: `data/local/dao/VideoDao.kt`, `DownloadDao.kt`, `HistoryDao.kt`, `PlaylistDao.kt`

**How**:

**AppDatabase.kt**:
```kotlin
package com.example.medianest.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.medianest.data.local.dao.VideoDao
import com.example.medianest.data.local.dao.DownloadDao
import com.example.medianest.data.local.dao.HistoryDao
import com.example.medianest.data.local.dao.PlaylistDao
import com.example.medianest.data.local.entity.VideoEntity
import com.example.medianest.data.local.entity.DownloadEntity
import com.example.medianest.data.local.entity.HistoryEntity
import com.example.medianest.data.local.entity.PlaylistEntity

@Database(
    entities = [
        VideoEntity::class,
        DownloadEntity::class,
        HistoryEntity::class,
        PlaylistEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun downloadDao(): DownloadDao
    abstract fun historyDao(): HistoryDao
    abstract fun playlistDao(): PlaylistDao
}
```

**VideoEntity.kt**:
```kotlin
package com.example.medianest.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val id: String,  // YouTube video ID
    val title: String,
    val channelName: String,
    val channelId: String? = null,
    val durationSeconds: Long = 0,
    val thumbnailUrl: String? = null,
    val description: String? = null,
    val uploadDate: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)
```

**DownloadEntity.kt**:
```kotlin
package com.example.medianest.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "downloads",
    foreignKeys = [
        ForeignKey(
            entity = VideoEntity::class,
            parentColumns = ["id"],
            childColumns = ["videoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("videoId")]
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: String,
    val filePath: String,
    val format: String,  // "audio" or "video"
    val quality: String, // e.g. "128kbps", "720p"
    val fileSizeBytes: Long = 0,
    val downloadedAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long? = null
)
```

**HistoryEntity.kt**:
```kotlin
package com.example.medianest.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playback_history",
    foreignKeys = [
        ForeignKey(
            entity = VideoEntity::class,
            parentColumns = ["id"],
            childColumns = ["videoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("videoId")]
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: String,
    val positionMillis: Long = 0,
    val playedAt: Long = System.currentTimeMillis()
)
```

**PlaylistEntity.kt**:
```kotlin
package com.example.medianest.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
```

**VideoDao.kt**:
```kotlin
package com.example.medianest.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.medianest.data.local.entity.VideoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos ORDER BY addedAt DESC")
    fun getAllVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE id = :videoId")
    suspend fun getVideoById(videoId: String): VideoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(video: VideoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(videos: List<VideoEntity>)

    @Update
    suspend fun update(video: VideoEntity)

    @Delete
    suspend fun delete(video: VideoEntity)
}
```

**DownloadDao.kt**:
```kotlin
package com.example.medianest.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.medianest.data.local.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY downloadedAt DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE videoId = :videoId")
    suspend fun getDownloadByVideoId(videoId: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity): Long

    @Update
    suspend fun update(download: DownloadEntity)

    @Delete
    suspend fun delete(download: DownloadEntity)
}
```

**HistoryDao.kt**:
```kotlin
package com.example.medianest.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.medianest.data.local.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM playback_history ORDER BY playedAt DESC")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM playback_history WHERE videoId = :videoId ORDER BY playedAt DESC LIMIT 1")
    suspend fun getLatestPlayback(videoId: String): HistoryEntity?

    @Insert
    suspend fun insert(history: HistoryEntity)

    @Query("DELETE FROM playback_history WHERE playedAt < :beforeTimestamp")
    suspend fun deleteOldEntries(beforeTimestamp: Long)
}
```

**PlaylistDao.kt**:
```kotlin
package com.example.medianest.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.medianest.data.local.entity.PlaylistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistById(playlistId: Long): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlist: PlaylistEntity): Long

    @Update
    suspend fun update(playlist: PlaylistEntity)

    @Delete
    suspend fun delete(playlist: PlaylistEntity)
}
```

**Why**: Room provides type-safe SQLite access. Entities model the core domain: videos fetched from YouTube, downloads stored locally, playback history, and user playlists.

**Edge cases**: Foreign keys cascade on delete — deleting a video removes its downloads and history. `exportSchema = true` generates schema files for migrations.

**Pitfalls / do not**: Do not use `autoGenerate = true` for `VideoEntity.id` — YouTube video IDs are the natural primary key. Do not use `List` directly in DAO without `Flow<>` for reactive queries.

**Validation**: Compiles with `ksp` task generating Room code.

**Docs**: Schema files generated to `app/schemas/` — git-ignore them or commit for migration tracking.

---

### Step 1.5: Create Hilt DI modules

**What**: Create `DatabaseModule.kt` providing `AppDatabase` and DAOs.

**Where**: `app/src/main/java/com/example/medianest/di/DatabaseModule.kt`

**How**:
```kotlin
package com.example.medianest.di

import android.content.Context
import androidx.room.Room
import com.example.medianest.data.local.AppDatabase
import com.example.medianest.data.local.dao.VideoDao
import com.example.medianest.data.local.dao.DownloadDao
import com.example.medianest.data.local.dao.HistoryDao
import com.example.medianest.data.local.dao.PlaylistDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "media_nest.db"
        ).build()
    }

    @Provides
    fun provideVideoDao(database: AppDatabase): VideoDao = database.videoDao()

    @Provides
    fun provideDownloadDao(database: AppDatabase): DownloadDao = database.downloadDao()

    @Provides
    fun provideHistoryDao(database: AppDatabase): HistoryDao = database.historyDao()

    @Provides
    fun providePlaylistDao(database: AppDatabase): PlaylistDao = database.playlistDao()
}
```

**Why**: Hilt SingletonComponent ensures single Room database instance per app.

**Edge cases**: `Room.databaseBuilder` without `fallbackToDestructiveMigration()` will crash on migration mismatch. For now, no migrations expected (v1). Add when schema changes.

**Pitfalls / do not**: Do not use `.allowMainThreadQueries()` — always use coroutines for DB access.

**Validation**: Compiles with Hilt processing.

**Docs**: None.

---

### Step 1.6: Create navigation scaffold + placeholder screens

**What**: Create `AppNavigation.kt` with `NavHost`, 4 placeholder screens (Home, Downloads, Library, Settings), `MainScreen.kt` with bottom navigation bar.

**Where**:
- `ui/navigation/AppNavigation.kt`
- `ui/navigation/BottomNavItem.kt`
- `ui/MainScreen.kt`
- `ui/screens/HomeScreen.kt`
- `ui/screens/DownloadsScreen.kt`
- `ui/screens/LibraryScreen.kt`
- `ui/screens/SettingsScreen.kt`

**How**:

**BottomNavItem.kt**:
```kotlin
package com.example.medianest.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Home : BottomNavItem("home", "Home", Icons.Default.Home)
    data object Downloads : BottomNavItem("downloads", "Downloads", Icons.Default.Download)
    data object Library : BottomNavItem("library", "Library", Icons.Default.LibraryMusic)
    data object Settings : BottomNavItem("settings", "Settings", Icons.Default.Settings)
}
```

**AppNavigation.kt**:
```kotlin
package com.example.medianest.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.medianest.ui.screens.HomeScreen
import com.example.medianest.ui.screens.DownloadsScreen
import com.example.medianest.ui.screens.LibraryScreen
import com.example.medianest.ui.screens.SettingsScreen

@Composable
fun AppNavigation(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = BottomNavItem.Home.route
    ) {
        composable(BottomNavItem.Home.route) { HomeScreen() }
        composable(BottomNavItem.Downloads.route) { DownloadsScreen() }
        composable(BottomNavItem.Library.route) { LibraryScreen() }
        composable(BottomNavItem.Settings.route) { SettingsScreen() }
    }
}
```

**MainScreen.kt**:
```kotlin
package com.example.medianest.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.medianest.ui.navigation.AppNavigation
import com.example.medianest.ui.navigation.BottomNavItem
import com.example.medianest.ui.theme.MediaNestTheme

@Composable
fun MainScreen() {
    MediaNestTheme {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        Scaffold(
            bottomBar = {
                NavigationBar {
                    listOf(
                        BottomNavItem.Home,
                        BottomNavItem.Downloads,
                        BottomNavItem.Library,
                        BottomNavItem.Settings
                    ).forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            AppNavigation(
                navController = navController,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
```

Note: `AppNavigation` needs a `modifier` parameter — update the signature:
```kotlin
fun AppNavigation(navController: NavHostController, modifier: Modifier = Modifier)
```
And pass it to `NavHost`:
```kotlin
NavHost(
    navController = navController,
    startDestination = BottomNavItem.Home.route,
    modifier = modifier
)
```

**HomeScreen.kt**:
```kotlin
package com.example.medianest.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun HomeScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Home")
    }
}
```

**DownloadsScreen.kt** (same pattern — "Downloads" text):
```kotlin
package com.example.medianest.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun DownloadsScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Downloads")
    }
}
```

**LibraryScreen.kt** (same pattern — "Library" text):
```kotlin
package com.example.medianest.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun LibraryScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Library")
    }
}
```

**SettingsScreen.kt** (same pattern — "Settings" text):
```kotlin
package com.example.medianest.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun SettingsScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Settings")
    }
}
```

**Why**: Navigation structure is the app shell; all future screens plug into this NavHost. Bottom nav provides primary navigation between 4 sections.

**Edge cases**: `Icons.Default.LibraryMusic` may not exist in Material Icons default set — falls back gracefully if not found (compile error, not runtime). If missing, use `Icons.Default.List` or `Icons.Default.Folder`.

**Pitfalls / do not**: Do not use `Icons.Filled` — `Icons.Default` is the default alias for `Icons.Filled`. Do not forget to update `AppNavigation` to accept `modifier`.

**Validation**: App launches with navigation working, 4 tabs switchable.

**Docs**: None.

---

### Step 1.7: Update `AndroidManifest.xml` + `MainActivity.kt`

**What**: Add internet permission, application class name, `@AndroidEntryPoint` annotation, hook `MainScreen` into activity.

**Where**: `AndroidManifest.xml` + `MainActivity.kt`

**How**:

**AndroidManifest.xml** — add before `<application>`:
```xml
<uses-permission android:name="android.permission.INTERNET" />
```

Add `android:name=".MediaNestApp"` to `<application>`:
```xml
<application
    android:name=".MediaNestApp"
    ...
```

**MainActivity.kt** — replace content:
```kotlin
package com.example.medianest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import com.example.medianest.ui.MainScreen

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MainScreen()
        }
    }
}
```

**Why**: `INTERNET` needed for YouTube API calls. `MediaNestApp` class required by Hilt. `@AndroidEntryPoint` enables Hilt injection in Activity.

**Edge cases**: `enableEdgeToEdge()` is from `androidx.activity` — already imported. `@AndroidEntryPoint` requires Hilt plugin applied to module.

**Pitfalls / do not**: Do not forget to remove the old `Greeting` composable and `MediaNestTheme` wrap (MainScreen handles it internally). Do not remove the `import com.example.medianest.ui.MainScreen` statement.

**Validation**: App compiles and launches to "Home" screen with bottom nav bar visible.

**Docs**: None.

---

### Step 1.8: Add `buildConfig = true` to `app/build.gradle.kts`

**What**: Enable `BuildConfig.DEBUG` flag in the `buildFeatures` block, used by `MediaNestApp` for Timber planting.

**Where**: `app/build.gradle.kts` — inside `android` block, in `buildFeatures`:
```kotlin
buildFeatures {
    compose = true
    buildConfig = true
}
```

**Why**: `MediaNestApp.kt` references `BuildConfig.DEBUG` to conditionally plant Timber. Without `buildConfig = true`, `BuildConfig` class won't contain `DEBUG` field.

**Pitfalls / do not**: Do not place this outside `android` block.

**Validation**: `BuildConfig.DEBUG` is accessible in code.

---

## Beginner Implementation Guide

1. Open `gradle/libs.versions.toml` — add all version numbers, library coordinates, and plugin definitions from Step 1.1
2. Open `build.gradle.kts` (root) — add 3 plugins with `apply false`
3. Open `app/build.gradle.kts` — apply 3 plugins, add `ksp` config block, add `buildConfig = true`, add all dependencies
4. Create `app/src/main/java/com/example/medianest/MediaNestApp.kt` from Step 1.3
5. Create directory `data/local/entity/` and `data/local/dao/` under the `com/example/medianest/` source path, add 4 entities + 4 DAOs (Step 1.4)
6. Create `data/local/AppDatabase.kt` (Step 1.4)
7. Create `di/DatabaseModule.kt` (Step 1.5)
8. Create `ui/navigation/BottomNavItem.kt` + `AppNavigation.kt`, `ui/MainScreen.kt`, `ui/screens/HomeScreen.kt`, `DownloadsScreen.kt`, `LibraryScreen.kt`, `SettingsScreen.kt` (Step 1.6)
9. Update `AndroidManifest.xml` — add internet permission + application class name (Step 1.7)
10. Replace `MainActivity.kt` content (Step 1.7)

## Final Verification Checklist

- [ ] `./gradlew :app:assembleDebug` succeeds
- [ ] App launches on emulator or device showing "Home" in center
- [ ] Bottom navigation bar shows 4 tabs: Home, Downloads, Library, Settings
- [ ] Tapping each tab navigates to the correct screen
- [ ] Room schema generated at `app/schemas/com.example.medianest.data.local.AppDatabase/1.json`
- [ ] No kapt-related deprecation warnings in build output

## Stop Conditions

- `./gradlew :app:assembleDebug` fails — stop and diagnose. Common causes:
  - KSP version mismatch with Kotlin → verify `2.2.10-1.0.33` exists at https://github.com/google/ksp/releases
  - Hilt version mismatch → verify `2.55` exists and supports KSP
  - Missing plugin application in root `build.gradle.kts`
  - `Icons.Default.LibraryMusic` not found → replace with `Icons.Default.Folder`
  - Room schema export path not writable → check `ksp` config block points to valid dir
- If Room generates migration warning about no migrations for v1 → add `fallbackToDestructiveMigration()` (acceptable for development)
- If any dependency fails to resolve → check Maven Central / Google availability or adjust version
