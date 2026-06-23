package com.example.medianest

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.medianest.ui.MainScreen
import com.example.medianest.ui.viewmodel.PendingRestartConfirmation
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleRestartIntent(intent)
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            if (checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(permission), 101)
            }
        }

        setContent {
            MainScreen()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleRestartIntent(intent)
    }

    private fun handleRestartIntent(intent: Intent?) {
        if (intent?.action == "com.example.medianest.ACTION_CONFIRM_RESTART") {
            val id = intent.getLongExtra("download_id", -1L)
            if (id != -1L) {
                // Pause download immediately so confirmation dialog remains stable
                val pauseIntent = Intent(this, com.example.medianest.service.DownloadService::class.java).apply {
                    action = com.example.medianest.service.DownloadService.ACTION_PAUSE
                    putExtra(com.example.medianest.service.DownloadService.EXTRA_DOWNLOAD_ID, id)
                }
                try {
                    androidx.core.content.ContextCompat.startForegroundService(this, pauseIntent)
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Failed to pause download before confirmation dialog", e)
                }
                PendingRestartConfirmation.pendingDownloadId.tryEmit(id)
                intent.action = null
            }
        } else if (intent?.action == "com.example.medianest.ACTION_NAVIGATE_DOWNLOADS") {
            PendingRestartConfirmation.navigateToDownloads.tryEmit(Unit)
            intent.action = null
        }
    }
}
