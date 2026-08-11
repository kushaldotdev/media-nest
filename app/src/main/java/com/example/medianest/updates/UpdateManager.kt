package com.example.medianest.updates

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.medianest.data.preferences.UpdatePreferences
import com.example.medianest.worker.UpdateDownloadWorker
import com.example.medianest.worker.WorkScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** UI-facing state of the app-update flow. */
sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data class UpdateAvailable(val latestVersion: String, val changelog: String, val downloadUrl: String) : UpdateState()
    data object NoUpdateAvailable : UpdateState()
    data class Downloading(val progress: Float) : UpdateState()
    data class Error(val message: String) : UpdateState()
    data object ReadyToInstall : UpdateState()
}

/** Result of a background (worker-initiated) update check. */
data class UpdateCheckResult(
    val updateAvailable: Boolean,
    val latestVersion: String = "",
    val changelog: String = "",
    val downloadUrl: String = "",
    val errorMessage: String? = null
)

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

/**
 * App-scoped source of truth for the app-update flow.
 *
 * Survives navigation (ViewModel death) and WorkManager pruning by merging:
 *  1. Persisted DataStore markers (ready/failed/downloading + url/version/changelog)
 *  2. APK file existence at filesDir/update.apk (gate for ReadyToInstall)
 *  3. WorkManager WorkInfo overlay (authoritative while RUNNING/ENQUEUED)
 *
 * The merge rule: baseline from markers + file check; then overlay WorkInfo —
 * RUNNING/ENQUEUED wins for Downloading(progress), SUCCEEDED -> ReadyToInstall
 * (ungated), FAILED -> Error, CANCELLED/empty -> keep baseline. Never resets to
 * Idle and never overrides an in-flight download.
 */
@Singleton
class UpdateManager @Inject constructor(
    private val updatePreferences: UpdatePreferences,
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val appContext: Context
) {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var isChecking = false

    val updateApkFile: File
        get() = File(appContext.filesDir, "update.apk")

    private val rateLimitMs = 5 * 60 * 1000L // 5 minutes

    init {
        scope.launch {
            seedBaselineFromPreferences()
        }
        scope.launch {
            observeWorkInfo()
        }
    }

    private suspend fun seedBaselineFromPreferences() {
        val state = updatePreferences.state.first()
        when (state) {
            UpdatePreferences.STATE_READY -> {
                if (updateApkFile.exists()) {
                    _updateState.value = UpdateState.ReadyToInstall
                } else {
                    _updateState.value = UpdateState.Error("Download lost — please re-download.")
                    updatePreferences.clearState()
                }
            }
            UpdatePreferences.STATE_DOWNLOADING -> {
                val progress = updatePreferences.progress.first()
                _updateState.value = UpdateState.Downloading(progress)
            }
            UpdatePreferences.STATE_FAILED -> {
                val error = updatePreferences.error.first()
                _updateState.value = UpdateState.Error(error.ifBlank { "Download failed." })
            }
            UpdatePreferences.STATE_UPDATE_AVAILABLE -> {
                val latest = updatePreferences.latestVersion.first()
                val changelog = updatePreferences.changelog.first()
                val url = updatePreferences.downloadUrl.first()
                if (latest.isNotBlank() && url.isNotBlank()) {
                    _updateState.value = UpdateState.UpdateAvailable(latest, changelog, url)
                } else {
                    _updateState.value = UpdateState.Idle
                }
            }
            else -> {
                _updateState.value = UpdateState.Idle
            }
        }
    }

    private suspend fun observeWorkInfo() {
        WorkManager.getInstance(appContext)
            .getWorkInfosForUniqueWorkFlow(WorkScheduler.UPDATE_DOWNLOAD_WORK_NAME)
            .collect { workInfos ->
                val info = workInfos.firstOrNull()
                if (info == null) {
                    // Empty/pruned list: keep the baseline from markers.
                    return@collect
                }
                when (info.state) {
                    WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> {
                        val progress = info.progress.getFloat(UpdateDownloadWorker.KEY_PROGRESS, 0f)
                        _updateState.value = UpdateState.Downloading(progress)
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        // Ungated: completed download must surface even on a fresh process.
                        if (updateApkFile.exists()) {
                            _updateState.value = UpdateState.ReadyToInstall
                        } else {
                            _updateState.value = UpdateState.Error("Download lost — please re-download.")
                        }
                    }
                    WorkInfo.State.FAILED -> {
                        val error = info.outputData.getString(UpdateDownloadWorker.KEY_ERROR) ?: "Download failed"
                        _updateState.value = UpdateState.Error(error)
                    }
                    else -> { /* CANCELLED or others: keep baseline */ }
                }
            }
    }

    /** Performs a check against the GitHub latest release API. No-op if a check is already in flight. */
    suspend fun checkForUpdates() {
        if (isChecking) return
        // Never clobber an in-flight or completed download with a check result.
        if (_updateState.value is UpdateState.Downloading || _updateState.value is UpdateState.ReadyToInstall) return
        // Rate-limit guard: skip when a check happened within 5 minutes AND the current
        // state is a successful check result (UpdateAvailable/NoUpdateAvailable) that is
        // still meaningful to show. Errors and Idle always allow a re-check (Try Again).
        val current = _updateState.value
        val canShowCached = current is UpdateState.UpdateAvailable || current is UpdateState.NoUpdateAvailable
        val lastCheck = updatePreferences.lastCheckAt.first()
        val withinRateLimit = lastCheck > 0 && System.currentTimeMillis() - lastCheck < rateLimitMs
        if (withinRateLimit && canShowCached) {
            return
        }
        isChecking = true
        _updateState.value = UpdateState.Checking
        try {
            val result = performCheckInternal()
            applyCheckResult(result)
        } finally {
            isChecking = false
        }
    }

    /**
     * Shared check logic usable from a worker (no UI dependency).
     * Returns a plain result instead of mutating UI state.
     */
    suspend fun performUpdateCheckForWorker(): UpdateCheckResult {
        return try {
            performCheckInternal()
        } catch (e: Exception) {
            UpdateCheckResult(
                updateAvailable = false,
                errorMessage = "Check failed: ${e.message}"
            )
        }
    }

    private suspend fun performCheckInternal(): UpdateCheckResult {
        val currentVersion = try {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }

        val request = Request.Builder()
            .url("https://api.github.com/repos/kushaldotdev/media-nest/releases/latest")
            .header("User-Agent", "MediaNest-App")
            .build()

        return try {
            // Never block the main dispatcher: the caller may be viewModelScope (Main).
            withContext(Dispatchers.IO) {
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val code = response.code
                        val message = if (code == 403) {
                            "GitHub rate limit reached. Please try again later."
                        } else {
                            "Server returned $code"
                        }
                        return@use UpdateCheckResult(
                            updateAvailable = false,
                            errorMessage = message
                        )
                    }
                    val bodyString = response.body?.string() ?: throw Exception("Empty response body")
                    val release = json.decodeFromString<GitHubRelease>(bodyString)
                    val latest = release.tag_name.removePrefix("v").trim()
                    val hasUpdate = isNewerVersion(currentVersion, latest)

                    if (hasUpdate) {
                        val apkAsset = release.assets.find { it.name.endsWith(".apk") }
                        if (apkAsset != null) {
                            updatePreferences.setUpdateInfo(latest, release.body ?: "No release notes provided.", apkAsset.browser_download_url)
                            updatePreferences.setState(UpdatePreferences.STATE_UPDATE_AVAILABLE)
                            updatePreferences.setLastCheckAt(System.currentTimeMillis())
                            UpdateCheckResult(
                                updateAvailable = true,
                                latestVersion = latest,
                                changelog = release.body ?: "No release notes provided.",
                                downloadUrl = apkAsset.browser_download_url
                            )
                        } else {
                            UpdateCheckResult(
                                updateAvailable = false,
                                errorMessage = "Update available ($latest) but no APK asset found."
                            )
                        }
                    } else {
                        updatePreferences.setLastCheckAt(System.currentTimeMillis())
                        UpdateCheckResult(updateAvailable = false)
                    }
                }
            }
        } catch (e: Exception) {
            UpdateCheckResult(
                updateAvailable = false,
                errorMessage = "Check failed: ${e.message}"
            )
        }
    }

    private suspend fun applyCheckResult(result: UpdateCheckResult) {
        if (result.errorMessage != null) {
            _updateState.value = UpdateState.Error(result.errorMessage)
        } else if (result.updateAvailable) {
            // persist the update-available marker so re-entry surfaces it
            updatePreferences.setState(UpdatePreferences.STATE_UPDATE_AVAILABLE)
            _updateState.value = UpdateState.UpdateAvailable(
                latestVersion = result.latestVersion,
                changelog = result.changelog,
                downloadUrl = result.downloadUrl
            )
        } else {
            updatePreferences.clearState()
            _updateState.value = UpdateState.NoUpdateAvailable
        }
    }

    suspend fun downloadAndInstallUpdate(downloadUrl: String) {
        // Defensive guard: debug builds must never download/install the release APK.
        if (com.example.medianest.BuildConfig.DEBUG) {
            _updateState.value = UpdateState.Error("Updates are disabled in debug builds.")
            return
        }
        _updateState.value = UpdateState.Downloading(0f)
        updatePreferences.setState(UpdatePreferences.STATE_DOWNLOADING, progress = 0f)
        updatePreferences.setDownloadUrl(downloadUrl)
        WorkScheduler.enqueueUpdateDownload(appContext, downloadUrl)
    }

    suspend fun installApk() {
        // Debug builds must never install the release APK (different package/signing).
        if (com.example.medianest.BuildConfig.DEBUG) {
            _updateState.value = UpdateState.Error("Updates are disabled in debug builds.")
            return
        }
        val file = updateApkFile
        if (!file.exists()) {
            _updateState.value = UpdateState.Error("Download lost — please re-download.")
            updatePreferences.clearState()
            return
        }
        try {
            val apkUri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            appContext.startActivity(intent)
            updatePreferences.clearState()
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error("Failed to launch package installer: ${e.message}")
        }
    }

    suspend fun retryDownload() {
        val url = updatePreferences.downloadUrl.first()
        if (url.isBlank()) {
            _updateState.value = UpdateState.Error("No download available. Check for updates first.")
            return
        }
        downloadAndInstallUpdate(url)
    }

    suspend fun cancel() {
        WorkScheduler.cancelUpdateDownload(appContext)
        updatePreferences.clearState()
        _updateState.value = UpdateState.Idle
    }

    suspend fun reset() {
        updatePreferences.clearState()
        _updateState.value = UpdateState.Idle
    }

    /** Prerelease-aware comparison: strips -rc/-beta/-alpha suffixes before comparing. */
    private fun isNewerVersion(current: String, latest: String): Boolean {
        val cleanCurrent = current.substringBefore('-').trim()
        val cleanLatest = latest.substringBefore('-').trim()
        val currParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }
        val lateParts = cleanLatest.split(".").mapNotNull { it.toIntOrNull() }
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
