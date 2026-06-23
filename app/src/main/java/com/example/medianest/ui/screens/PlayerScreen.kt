package com.example.medianest.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import androidx.media3.ui.PlayerView
import com.example.medianest.ui.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    videoId: String,
    streamIndex: Int,
    viewModel: PlayerViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()

    var showResumeButton by rememberSaveable(videoId) { mutableStateOf(true) }
    var localPosition by remember { mutableStateOf<Float?>(null) }
    var isFullScreen by rememberSaveable { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val activity = context as? android.app.Activity
    DisposableEffect(isFullScreen) {
        activity?.requestedOrientation = if (isFullScreen) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        val window = activity?.window
        if (window != null) {
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            if (isFullScreen) {
                insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            val win = activity?.window
            if (win != null) {
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(win, win.decorView)
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(showControls, state.isPlaying) {
        if (showControls && state.isPlaying) {
            kotlinx.coroutines.delay(3000)
            showControls = false
        }
    }

    LaunchedEffect(videoId, streamIndex) {
        viewModel.initialize(videoId, streamIndex)
    }

    if (isFullScreen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                    }
                },
                update = { playerView ->
                    playerView.player = player
                },
                onRelease = { playerView ->
                    playerView.player = null
                },
                modifier = Modifier.fillMaxSize()
            )
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { showControls = !showControls }
            )

            if (state.isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }

            if (showControls) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { isFullScreen = false }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = state.title,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1
                            )
                        }
                        IconButton(onClick = { isFullScreen = false }) {
                            Icon(Icons.Default.FullscreenExit, contentDescription = "Exit Fullscreen", tint = Color.White)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(0.7f),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.seekRelative(-30_000L) }) {
                            Icon(Icons.Default.Replay30, contentDescription = "Rewind 30s", tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                        IconButton(onClick = { viewModel.seekRelative(-5_000L) }) {
                            Icon(Icons.Default.Replay5, contentDescription = "Rewind 5s", tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                        IconButton(
                            onClick = { viewModel.togglePlayPause() },
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        IconButton(onClick = { viewModel.seekRelative(5_000L) }) {
                            Icon(Icons.Default.Forward5, contentDescription = "Forward 5s", tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                        IconButton(onClick = { viewModel.seekRelative(30_000L) }) {
                            Icon(Icons.Default.Forward30, contentDescription = "Forward 30s", tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            LinearProgressIndicator(
                                progress = { (state.bufferedPositionMs.toFloat() / maxOf(state.durationMs, 1L).toFloat()).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp)
                                    .height(4.dp),
                                color = Color.White.copy(alpha = 0.3f),
                                trackColor = Color.Transparent
                            )
                            Slider(
                                value = localPosition ?: state.positionMs.toFloat(),
                                onValueChange = { localPosition = it },
                                onValueChangeFinished = {
                                    localPosition?.let {
                                        viewModel.seekTo(it.toLong())
                                        localPosition = null
                                    }
                                },
                                valueRange = 0f..maxOf(state.durationMs, 1L).toFloat(),
                                colors = SliderDefaults.colors(
                                    activeTrackColor = Color.White,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(formatDuration((localPosition ?: state.positionMs.toFloat()).toLong()), color = Color.White)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(formatDuration(state.durationMs), color = Color.White)
                                Spacer(Modifier.width(8.dp))
                                IconButton(onClick = { isFullScreen = false }) {
                                    Icon(Icons.Default.FullscreenExit, contentDescription = "Exit Fullscreen", tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(state.title, maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (state.isAudioOnly) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = state.thumbnailUrl,
                                contentDescription = state.title,
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .aspectRatio(1f)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        useController = false
                                    }
                                },
                                update = { playerView ->
                                    playerView.player = player
                                },
                                onRelease = { playerView ->
                                    playerView.player = null
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { viewModel.togglePlayPause() }
                            )

                            if (state.isBuffering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.align(Alignment.Center),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        if (state.historyPositionMs > 5000L && state.historyPositionMs < state.durationMs - 10000L && showResumeButton) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Last watched: ${formatDuration(state.historyPositionMs)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        TextButton(onClick = {
                                            viewModel.seekTo(state.historyPositionMs)
                                            showResumeButton = false
                                            viewModel.clearHistoryPosition()
                                        }) {
                                            Text("Resume", color = MaterialTheme.colorScheme.primary)
                                        }
                                        Spacer(Modifier.width(4.dp))
                                        TextButton(onClick = {
                                            viewModel.forceSaveCurrentPosition()
                                            showResumeButton = false
                                        }) {
                                            Text("Update", color = MaterialTheme.colorScheme.primary)
                                        }
                                        Spacer(Modifier.width(4.dp))
                                        IconButton(onClick = {
                                            showResumeButton = false
                                            viewModel.clearHistoryPosition()
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Dismiss",
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (state.showWatchedAlertCount != null) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Watched ${state.showWatchedAlertCount} times",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    IconButton(onClick = { viewModel.dismissWatchedAlert() }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Dismiss",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            LinearProgressIndicator(
                                progress = { (state.bufferedPositionMs.toFloat() / maxOf(state.durationMs, 1L).toFloat()).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp)
                                    .height(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                trackColor = Color.Transparent
                            )
                            Slider(
                                value = localPosition ?: state.positionMs.toFloat(),
                                onValueChange = { localPosition = it },
                                onValueChangeFinished = {
                                    localPosition?.let {
                                        viewModel.seekTo(it.toLong())
                                        localPosition = null
                                    }
                                },
                                valueRange = 0f..maxOf(state.durationMs, 1L).toFloat(),
                                colors = SliderDefaults.colors(
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(formatDuration((localPosition ?: state.positionMs.toFloat()).toLong()))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(formatDuration(state.durationMs))
                                Spacer(Modifier.width(8.dp))
                                IconButton(onClick = { isFullScreen = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Fullscreen,
                                        contentDescription = "Fullscreen",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { viewModel.seekRelative(-30_000L) }) {
                                Icon(Icons.Default.Replay30, contentDescription = "Rewind 30s")
                            }
                            IconButton(onClick = { viewModel.seekRelative(-5_000L) }) {
                                Icon(Icons.Default.Replay5, contentDescription = "Rewind 5s")
                            }
                            IconButton(
                                onClick = { viewModel.togglePlayPause() },
                                modifier = Modifier.size(64.dp)
                            ) {
                                Icon(
                                    if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            IconButton(onClick = { viewModel.seekRelative(5_000L) }) {
                                Icon(Icons.Default.Forward5, contentDescription = "Forward 5s")
                            }
                            IconButton(onClick = { viewModel.seekRelative(30_000L) }) {
                                Icon(Icons.Default.Forward30, contentDescription = "Forward 30s")
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Text("Speed", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                FilterChip(
                                    selected = state.currentSpeed == speed,
                                    onClick = { viewModel.setSpeed(speed) },
                                    label = { Text("${speed}x") }
                                )
                            }
                        }
                    }
                }

                state.error?.let { errorMsg ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.7f))
                            .clickable { viewModel.resetError() },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(errorMsg, color = Color.White)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { viewModel.retry() }) {
                                Text("Retry")
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("Tap to dismiss", color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
