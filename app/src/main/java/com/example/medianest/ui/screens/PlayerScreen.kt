package com.example.medianest.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.res.painterResource
import com.example.medianest.R
import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import com.example.medianest.ui.components.MediaNestTopAppBar
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.SizeTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
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
import androidx.media3.ui.AspectRatioFrameLayout
import com.example.medianest.ui.viewmodel.PlayerViewModel
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import com.example.medianest.ui.theme.MediaNestColors
import com.example.medianest.ui.theme.MediaNestSemanticColors
import com.example.medianest.ui.utils.UiUtils

private fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}

/**
 * Item representation for the Up Next queue in the player.
 */
data class PlayerQueueItem(
    val id: String,
    val title: String,
    val channelName: String = "",
    val durationSeconds: Long = 0L,
    val thumbnailUrl: String? = null,
    val isPlaying: Boolean = false,
    val downloadId: Long? = null,
    val streamIndex: Int = 0,
    val originalIndex: Int? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    videoId: String,
    streamIndex: Int,
    downloadId: Long? = null,
    viewModel: PlayerViewModel = hiltViewModel(),
    onBack: () -> Unit,
    queue: List<PlayerQueueItem> = emptyList(),
    contextTitle: String? = null,
    contextType: String? = null,
    autoplayNext: Boolean = true,
    onQueueReordered: ((List<PlayerQueueItem>) -> Unit)? = null,
    onQueueItemClick: ((PlayerQueueItem) -> Unit)? = null,
    onAutoplayToggle: ((Boolean) -> Unit)? = null,
    onRemoveFromQueue: ((PlayerQueueItem) -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val vmQueue by viewModel.queue.collectAsStateWithLifecycle()
    val vmContextTitle by viewModel.queueContextTitle.collectAsStateWithLifecycle()
    val vmContextType by viewModel.queueContextType.collectAsStateWithLifecycle()
    val effectiveQueue = if (queue.isNotEmpty()) queue else vmQueue
    val effectiveContextTitle = contextTitle ?: vmContextTitle
    val effectiveContextType = contextType ?: vmContextType

    val indexedQueue = remember(effectiveQueue) {
        effectiveQueue.mapIndexed { idx, itm ->
            if (itm.originalIndex == null) itm.copy(originalIndex = idx + 1) else itm
        }
    }
    var currentQueue by remember(indexedQueue) { mutableStateOf(indexedQueue) }
    var isAutoplayEnabled by remember(autoplayNext) { mutableStateOf(autoplayNext) }
    var showQueueSheet by remember { mutableStateOf(false) }
    val hasQueueContext = currentQueue.isNotEmpty() || !effectiveContextTitle.isNullOrEmpty() || !effectiveContextType.isNullOrEmpty()

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex in currentQueue.indices && toIndex in currentQueue.indices && fromIndex != toIndex) {
            val updated = currentQueue.toMutableList()
            val item = updated.removeAt(fromIndex)
            updated.add(toIndex, item)
            currentQueue = updated
            onQueueReordered?.invoke(updated)
        }
    }

    val isPlaybackEnded = player?.playbackState == androidx.media3.common.Player.STATE_ENDED
    var hasTriggeredAutoplay by remember(state.videoId) { mutableStateOf(false) }

    LaunchedEffect(isPlaybackEnded, isAutoplayEnabled, currentQueue) {
        if (isPlaybackEnded && isAutoplayEnabled && !hasTriggeredAutoplay && currentQueue.isNotEmpty()) {
            val currentIndex = currentQueue.indexOfFirst { it.id == state.videoId }
            val nextIndex = if (currentIndex != -1 && currentIndex + 1 < currentQueue.size) {
                currentIndex + 1
            } else if (currentIndex == -1 && currentQueue.isNotEmpty()) {
                0
            } else {
                -1
            }
            if (nextIndex in currentQueue.indices) {
                hasTriggeredAutoplay = true
                val nextItem = currentQueue[nextIndex]
                if (onQueueItemClick != null) {
                    onQueueItemClick(nextItem)
                } else {
                    viewModel.initialize(nextItem.id, nextItem.streamIndex, nextItem.downloadId)
                }
            }
        }
    }

    var showResumeButton by rememberSaveable(videoId) { mutableStateOf(true) }
    var localPosition by remember { mutableStateOf<Float?>(null) }
    var isFullScreen by rememberSaveable { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var timeDisplayMode by rememberSaveable { mutableStateOf(0) } // 0 = Elapsed, 1 = Remaining, 2 = Both
    var isTitleExpanded by rememberSaveable { mutableStateOf(false) }

    var showLeftSeekOverlay by remember { mutableStateOf(false) }
    var showRightSeekOverlay by remember { mutableStateOf(false) }
    var leftSeekTrigger by remember { mutableStateOf(0) }
    var rightSeekTrigger by remember { mutableStateOf(0) }

    var prevIsPlaying by remember { mutableStateOf(state.isPlaying) }
    var showPlayPauseOverlay by remember { mutableStateOf<Boolean?>(null) }
    var overlayTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(state.isPlaying) {
        if (state.isPlaying != prevIsPlaying) {
            showPlayPauseOverlay = state.isPlaying
            overlayTrigger++
            prevIsPlaying = state.isPlaying
        }
    }

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
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

    LaunchedEffect(videoId, streamIndex, downloadId) {
        viewModel.initialize(videoId, streamIndex, downloadId)
    }

    if (isFullScreen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MediaNestColors.PlayerSurface)
        ) {
            if (state.isAudioOnly) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight(0.7f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        AsyncImage(
                            model = state.thumbnailUrl,
                            contentDescription = state.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            modifier = Modifier.padding(bottom = 12.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MediaNestColors.PlayerSurface.copy(alpha = 0.8f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AudioEqualizerBars(isPlaying = state.isPlaying)
                                Text(
                                    text = if (state.isPlaying) "Playing audio" else "Audio paused",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MediaNestColors.TextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                        }
                    },
                    update = { playerView ->
                        playerView.player = player
                        playerView.keepScreenOn = state.isPlaying
                    },
                    onRelease = { playerView ->
                        playerView.player = null
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { offset ->
                                val isLeftHalf = offset.x < size.width / 2f
                                if (isLeftHalf) {
                                    showLeftSeekOverlay = true
                                    leftSeekTrigger++
                                    viewModel.seekRelative(-10_000L)
                                } else {
                                    showRightSeekOverlay = true
                                    rightSeekTrigger++
                                    viewModel.seekRelative(10_000L)
                                }
                            },
                            onTap = {
                                showControls = !showControls
                            }
                        )
                    }
            )

            showPlayPauseOverlay?.let { playing ->
                var visible by remember(overlayTrigger) { mutableStateOf(true) }
                LaunchedEffect(overlayTrigger) {
                    kotlinx.coroutines.delay(500)
                    visible = false
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn() + scaleIn(initialScale = 0.5f),
                    exit = fadeOut() + scaleOut(targetScale = 0.5f),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MediaNestColors.PlayerSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(if (playing) R.drawable.ic_mn_play else R.drawable.ic_mn_pause),
                                contentDescription = null,
                                tint = MediaNestColors.TextPrimary,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }
            }

            // Left Seek Overlay (Rewind 10s) - opposite side (CenterEnd), no backdrop
            if (showLeftSeekOverlay) {
                var visible by remember(leftSeekTrigger) { mutableStateOf(true) }
                LaunchedEffect(leftSeekTrigger) {
                    kotlinx.coroutines.delay(650)
                    visible = false
                    showLeftSeekOverlay = false
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .fillMaxWidth(0.4f)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(R.drawable.ic_mn_rewind10),
                                contentDescription = null,
                                tint = MediaNestColors.TextPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "-10s",
                                color = MediaNestColors.TextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Right Seek Overlay (Forward 10s) - opposite side (CenterStart), no backdrop
            if (showRightSeekOverlay) {
                var visible by remember(rightSeekTrigger) { mutableStateOf(true) }
                LaunchedEffect(rightSeekTrigger) {
                    kotlinx.coroutines.delay(650)
                    visible = false
                    showRightSeekOverlay = false
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .fillMaxWidth(0.4f)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(R.drawable.ic_mn_forward10),
                                contentDescription = null,
                                tint = MediaNestColors.TextPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "+10s",
                                color = MediaNestColors.TextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (state.isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MediaNestColors.TextPrimary
                )
            }

            if (showControls) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MediaNestColors.PlayerSurface.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                            IconButton(onClick = { isFullScreen = false }) {
                                Icon(painter = painterResource(R.drawable.ic_mn_back), contentDescription = "Back", tint = MediaNestColors.TextPrimary)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = state.title,
                                color = MediaNestColors.TextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = if (isTitleExpanded) Int.MAX_VALUE else 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .clickable { isTitleExpanded = !isTitleExpanded }
                            )
                            val watchCount = state.watchCount
                            if (watchCount > 0) {
                                Spacer(Modifier.width(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_mn_eye),
                                        contentDescription = null,
                                        tint = MediaNestColors.TextPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "$watchCount",
                                        color = MediaNestColors.TextPrimary,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                            val quality = state.videoQuality
                            val qualityText = if (!quality.isNullOrEmpty()) {
                                if (state.isLocal) "$quality • Local" else "$quality • Stream"
                            } else {
                                if (state.isLocal) "Local" else "Stream"
                            }
                            if (qualityText.isNotEmpty()) {
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    color = MediaNestColors.TextPrimary.copy(alpha = 0.2f),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = qualityText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MediaNestColors.TextPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { isFullScreen = false }) {
                                Icon(painter = painterResource(R.drawable.ic_mn_fullscreen), contentDescription = "Exit Fullscreen", tint = MediaNestColors.TextPrimary)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(0.85f),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.seekRelative(-30_000L) }) {
                            Icon(painter = painterResource(R.drawable.ic_mn_rewind30), contentDescription = "Rewind 30s", tint = MediaNestColors.TextPrimary, modifier = Modifier.size(36.dp))
                        }
                        IconButton(onClick = { viewModel.seekRelative(-5_000L) }) {
                            Icon(painter = painterResource(R.drawable.ic_mn_rewind5), contentDescription = "Rewind 5s", tint = MediaNestColors.TextPrimary, modifier = Modifier.size(36.dp))
                        }
                        val fsPrevIdx = currentQueue.indexOfFirst { it.id == state.videoId }
                        IconButton(
                            onClick = {
                                if (fsPrevIdx > 0) {
                                    val p = currentQueue[fsPrevIdx - 1]
                                    if (onQueueItemClick != null) onQueueItemClick(p) else viewModel.initialize(p.id, p.streamIndex, p.downloadId)
                                }
                            },
                            enabled = fsPrevIdx > 0
                        ) {
                            Icon(painter = painterResource(R.drawable.ic_mn_prev), contentDescription = "Previous Track", tint = MediaNestColors.TextPrimary, modifier = Modifier.size(36.dp))
                        }
                        IconButton(
                            onClick = { viewModel.togglePlayPause() },
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(
                                painter = painterResource(if (state.isPlaying) R.drawable.ic_mn_pause else R.drawable.ic_mn_play),
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                tint = MediaNestColors.TextPrimary,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        val fsNextIdx = currentQueue.indexOfFirst { it.id == state.videoId }
                        IconButton(
                            onClick = {
                                if (fsNextIdx != -1 && fsNextIdx + 1 < currentQueue.size) {
                                    val n = currentQueue[fsNextIdx + 1]
                                    if (onQueueItemClick != null) onQueueItemClick(n) else viewModel.initialize(n.id, n.streamIndex, n.downloadId)
                                }
                            },
                            enabled = fsNextIdx != -1 && fsNextIdx + 1 < currentQueue.size
                        ) {
                            Icon(painter = painterResource(R.drawable.ic_mn_next), contentDescription = "Next Track", tint = MediaNestColors.TextPrimary, modifier = Modifier.size(36.dp))
                        }
                        IconButton(onClick = { viewModel.seekRelative(5_000L) }) {
                            Icon(painter = painterResource(R.drawable.ic_mn_forward5), contentDescription = "Forward 5s", tint = MediaNestColors.TextPrimary, modifier = Modifier.size(36.dp))
                        }
                        IconButton(onClick = { viewModel.seekRelative(30_000L) }) {
                            Icon(painter = painterResource(R.drawable.ic_mn_forward30), contentDescription = "Forward 30s", tint = MediaNestColors.TextPrimary, modifier = Modifier.size(36.dp))
                        }
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
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
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                            track = { sliderState ->
                                val fraction = if (state.durationMs > 0) (sliderState.value / state.durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                                val bufferedFraction = if (state.durationMs > 0) (state.bufferedPositionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                                Canvas(Modifier.fillMaxWidth().height(16.dp)) {
                                    val trackH = 4.dp.toPx()
                                    val top = (size.height - trackH) / 2f
                                    val r = CornerRadius(2.dp.toPx())
                                    drawRoundRect(color = Color(0xFF54333C), topLeft = Offset(0f, top), size = Size(size.width, trackH), cornerRadius = r)
                                    drawRoundRect(color = Color.White.copy(alpha = 0.18f), topLeft = Offset(0f, top), size = Size(size.width * bufferedFraction, trackH), cornerRadius = r)
                                    drawRoundRect(color = MediaNestColors.YouTubeRed, topLeft = Offset(0f, top), size = Size(size.width * fraction, trackH), cornerRadius = r)
                                }
                            },
                            thumb = {
                                Box(
                                    modifier = Modifier.size(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        Modifier
                                            .size(14.dp)
                                            .background(MediaNestColors.YouTubeRed, CircleShape)
                                            .border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                    )
                                }
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = MediaNestColors.YouTubeRed,
                                activeTrackColor = Color.Transparent,
                                inactiveTrackColor = Color.Transparent
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val currentPos = (localPosition ?: state.positionMs.toFloat()).toLong()
                            val displayTime = when (timeDisplayMode) {
                                1 -> {
                                    val remaining = state.durationMs - currentPos
                                    if (remaining <= 0) "-0s" else "-${formatDuration(remaining)}"
                                }
                                2 -> {
                                    val remaining = state.durationMs - currentPos
                                    val remStr = if (remaining <= 0) "-0s" else "-${formatDuration(remaining)}"
                                    "${formatDuration(currentPos)} / $remStr"
                                }
                                else -> formatDuration(currentPos)
                            }
                            Text(
                                text = displayTime,
                                color = MediaNestColors.TextPrimary,
                                modifier = Modifier.clickable { timeDisplayMode = (timeDisplayMode + 1) % 3 }
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(formatDuration(state.durationMs), color = MediaNestColors.TextPrimary)
                                Spacer(Modifier.width(8.dp))
                                IconButton(onClick = { isFullScreen = false }) {
                                    Icon(painter = painterResource(R.drawable.ic_mn_fullscreen), contentDescription = "Exit Fullscreen", tint = MediaNestColors.TextPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        val videoAspect = if (state.videoWidth > 0 && state.videoHeight > 0) state.videoWidth.toFloat() / state.videoHeight.toFloat() else 16f / 9f
        Scaffold(
            topBar = {
                MediaNestTopAppBar(
                    title = "",
                    onNavigateBack = onBack,
                    actions = {
                        val watchCount = state.watchCount
                        if (watchCount > 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_eye),
                                    contentDescription = null,
                                    tint = MediaNestColors.Accent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "$watchCount",
                                    color = MediaNestColors.TextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        val quality = state.videoQuality
                        if (!quality.isNullOrEmpty()) {
                            val qualityText = if (state.isLocal) "$quality • Local" else "$quality • Stream"
                            Surface(
                                color = MediaNestColors.Card,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = qualityText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MediaNestColors.TextSecondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                        IconButton(onClick = { isFullScreen = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_mn_fullscreen),
                                contentDescription = "Fullscreen",
                                tint = MediaNestColors.TextPrimary
                            )
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
                    modifier = if (videoAspect < 1f) {
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    } else {
                        Modifier.fillMaxSize()
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(videoAspect),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        if (state.isAudioOnly) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = {
                                                viewModel.togglePlayPause()
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.65f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(20.dp)),
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    AsyncImage(
                                        model = state.thumbnailUrl,
                                        contentDescription = state.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Surface(
                                        modifier = Modifier.padding(bottom = 12.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        color = MediaNestColors.PlayerSurface.copy(alpha = 0.8f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            AudioEqualizerBars(isPlaying = state.isPlaying)
                                            Text(
                                                text = if (state.isPlaying) "Playing audio" else "Audio paused",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MediaNestColors.TextPrimary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        useController = false
                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                },
                                update = { playerView ->
                                    playerView.player = player
                                    playerView.keepScreenOn = state.isPlaying
                                },
                                onRelease = { playerView ->
                                    playerView.player = null
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onDoubleTap = { offset ->
                                                val isLeftHalf = offset.x < size.width / 2f
                                                if (isLeftHalf) {
                                                    showLeftSeekOverlay = true
                                                    leftSeekTrigger++
                                                    viewModel.seekRelative(-10_000L)
                                                } else {
                                                    showRightSeekOverlay = true
                                                    rightSeekTrigger++
                                                    viewModel.seekRelative(10_000L)
                                                }
                                            },
                                            onTap = {
                                                viewModel.togglePlayPause()
                                            }
                                        )
                                    }
                            )

                            showPlayPauseOverlay?.let { playing ->
                                var visible by remember(overlayTrigger) { mutableStateOf(true) }
                                LaunchedEffect(overlayTrigger) {
                                    kotlinx.coroutines.delay(500)
                                    visible = false
                                }
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = visible,
                                    enter = fadeIn() + scaleIn(initialScale = 0.5f),
                                    exit = fadeOut() + scaleOut(targetScale = 0.5f),
                                    modifier = Modifier.align(Alignment.Center)
                                ) {
                                    Surface(
                                        shape = androidx.compose.foundation.shape.CircleShape,
                                        color = MediaNestColors.PlayerSurface.copy(alpha = 0.6f),
                                        modifier = Modifier.size(72.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                painter = painterResource(if (playing) R.drawable.ic_mn_play else R.drawable.ic_mn_pause),
                                                contentDescription = null,
                                                tint = MediaNestColors.TextPrimary,
                                                modifier = Modifier.size(40.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Left Seek Overlay (Rewind 10s) - opposite side (CenterEnd), no backdrop
                            if (showLeftSeekOverlay) {
                                var visible by remember(leftSeekTrigger) { mutableStateOf(true) }
                                LaunchedEffect(leftSeekTrigger) {
                                    kotlinx.coroutines.delay(650)
                                    visible = false
                                    showLeftSeekOverlay = false
                                }
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = visible,
                                    enter = fadeIn(),
                                    exit = fadeOut(),
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .fillMaxHeight()
                                        .fillMaxWidth(0.4f)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_mn_rewind10),
                                                contentDescription = null,
                                                tint = MediaNestColors.TextPrimary,
                                                modifier = Modifier.size(36.dp)
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                text = "-10s",
                                                color = MediaNestColors.TextPrimary,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            // Right Seek Overlay (Forward 10s) - opposite side (CenterStart), no backdrop
                            if (showRightSeekOverlay) {
                                var visible by remember(rightSeekTrigger) { mutableStateOf(true) }
                                LaunchedEffect(rightSeekTrigger) {
                                    kotlinx.coroutines.delay(650)
                                    visible = false
                                    showRightSeekOverlay = false
                                }
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = visible,
                                    enter = fadeIn(),
                                    exit = fadeOut(),
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .fillMaxHeight()
                                        .fillMaxWidth(0.4f)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_mn_forward10),
                                                contentDescription = null,
                                                tint = MediaNestColors.TextPrimary,
                                                modifier = Modifier.size(36.dp)
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                text = "+10s",
                                                color = MediaNestColors.TextPrimary,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

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
                            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
                    ) {
                        // Meta block: Title, Channel
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            Text(
                                text = state.title,
                                maxLines = if (isTitleExpanded) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isTitleExpanded = !isTitleExpanded }
                            )
                            if (state.channelName.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = state.channelName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

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
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                            track = { sliderState ->
                                val fraction = if (state.durationMs > 0) (sliderState.value / state.durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                                val bufferedFraction = if (state.durationMs > 0) (state.bufferedPositionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                                Canvas(Modifier.fillMaxWidth().height(16.dp)) {
                                    val trackH = 4.dp.toPx()
                                    val top = (size.height - trackH) / 2f
                                    val r = CornerRadius(2.dp.toPx())
                                    drawRoundRect(color = Color(0xFF54333C), topLeft = Offset(0f, top), size = Size(size.width, trackH), cornerRadius = r)
                                    drawRoundRect(color = Color.White.copy(alpha = 0.18f), topLeft = Offset(0f, top), size = Size(size.width * bufferedFraction, trackH), cornerRadius = r)
                                    drawRoundRect(color = MediaNestColors.YouTubeRed, topLeft = Offset(0f, top), size = Size(size.width * fraction, trackH), cornerRadius = r)
                                }
                            },
                            thumb = {
                                Box(
                                    modifier = Modifier.size(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        Modifier
                                            .size(14.dp)
                                            .background(MediaNestColors.YouTubeRed, CircleShape)
                                            .border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                    )
                                }
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = MediaNestColors.YouTubeRed,
                                activeTrackColor = Color.Transparent,
                                inactiveTrackColor = Color.Transparent
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val currentPos = (localPosition ?: state.positionMs.toFloat()).toLong()
                            val displayTime = when (timeDisplayMode) {
                                1 -> {
                                    val remaining = state.durationMs - currentPos
                                    if (remaining <= 0) "-0s" else "-${formatDuration(remaining)}"
                                }
                                2 -> {
                                    val remaining = state.durationMs - currentPos
                                    val remStr = if (remaining <= 0) "-0s" else "-${formatDuration(remaining)}"
                                    "${formatDuration(currentPos)} / $remStr"
                                }
                                else -> formatDuration(currentPos)
                            }
                            Text(
                                text = displayTime,
                                modifier = Modifier.clickable { timeDisplayMode = (timeDisplayMode + 1) % 3 }
                            )
                            Text(formatDuration(state.durationMs))
                        }

                        Spacer(Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { viewModel.seekRelative(-30_000L) }) {
                                Icon(painter = painterResource(R.drawable.ic_mn_rewind30), contentDescription = "Rewind 30s", tint = MediaNestColors.TextPrimary)
                            }
                            IconButton(onClick = { viewModel.seekRelative(-5_000L) }) {
                                Icon(painter = painterResource(R.drawable.ic_mn_rewind5), contentDescription = "Rewind 5s", tint = MediaNestColors.TextPrimary)
                            }
                            val nfsPrevIdx = currentQueue.indexOfFirst { it.id == state.videoId }
                            IconButton(
                                onClick = {
                                    if (nfsPrevIdx > 0) {
                                        val p = currentQueue[nfsPrevIdx - 1]
                                        if (onQueueItemClick != null) onQueueItemClick(p) else viewModel.initialize(p.id, p.streamIndex, p.downloadId)
                                    }
                                },
                                enabled = nfsPrevIdx > 0
                            ) {
                                Icon(painter = painterResource(R.drawable.ic_mn_prev), contentDescription = "Previous Track", tint = MediaNestColors.TextPrimary)
                            }
                            IconButton(
                                onClick = { viewModel.togglePlayPause() },
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    painter = painterResource(if (state.isPlaying) R.drawable.ic_mn_pause else R.drawable.ic_mn_play),
                                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                                    tint = MediaNestColors.Accent,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            val nfsNextIdx = currentQueue.indexOfFirst { it.id == state.videoId }
                            IconButton(
                                onClick = {
                                    if (nfsNextIdx != -1 && nfsNextIdx + 1 < currentQueue.size) {
                                        val n = currentQueue[nfsNextIdx + 1]
                                        if (onQueueItemClick != null) onQueueItemClick(n) else viewModel.initialize(n.id, n.streamIndex, n.downloadId)
                                    }
                                },
                                enabled = nfsNextIdx != -1 && nfsNextIdx + 1 < currentQueue.size
                            ) {
                                Icon(painter = painterResource(R.drawable.ic_mn_next), contentDescription = "Next Track", tint = MediaNestColors.TextPrimary)
                            }
                            IconButton(onClick = { viewModel.seekRelative(5_000L) }) {
                                Icon(painter = painterResource(R.drawable.ic_mn_forward5), contentDescription = "Forward 5s", tint = MediaNestColors.TextPrimary)
                            }
                            IconButton(onClick = { viewModel.seekRelative(30_000L) }) {
                                Icon(painter = painterResource(R.drawable.ic_mn_forward30), contentDescription = "Forward 30s", tint = MediaNestColors.TextPrimary)
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Segmented Options Row (Speed / Quality / Queue)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Speed Selector
                                var showSpeedMenu by remember { mutableStateOf(false) }
                                val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .combinedClickable(
                                            onClick = {
                                                val nextSpeedIndex = (speeds.indexOf(state.currentSpeed) + 1) % speeds.size
                                                viewModel.setSpeed(speeds[nextSpeedIndex])
                                            },
                                            onLongClick = { showSpeedMenu = true }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_mn_speed),
                                            contentDescription = "Playback Speed",
                                            tint = MediaNestColors.Accent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "${state.currentSpeed}x",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showSpeedMenu,
                                        onDismissRequest = { showSpeedMenu = false }
                                    ) {
                                        speeds.forEach { speed ->
                                            DropdownMenuItem(
                                                text = { Text("${speed}x") },
                                                onClick = {
                                                    viewModel.setSpeed(speed)
                                                    showSpeedMenu = false
                                                }
                                            )
                                        }
                                    }
                                }

                                if (!state.isAudioOnly) {
                                    // Divider between Speed and Quality
                                    androidx.compose.material3.VerticalDivider(
                                        modifier = Modifier.height(24.dp),
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                    )

                                    // Quality Selector
                                    var showQualityMenu by remember { mutableStateOf(false) }
                                    val videoStreams = remember(state.availableStreams) {
                                        val recommendedQualities = listOf("1080p", "720p", "480p", "360p", "240p", "144p")
                                        state.availableStreams
                                            .filter { it.format == "video" || it.format == "video_only" }
                                            .groupBy { stream ->
                                                recommendedQualities.firstOrNull { req -> stream.quality.startsWith(req) }
                                                    ?: stream.quality.takeWhile { it.isDigit() }.let { if (it.isEmpty()) "Unknown" else "${it}p" }
                                            }
                                            .mapNotNull { (resName, streams) ->
                                                if (resName == "Unknown") null else {
                                                    streams.maxByOrNull { (it.contentLength ?: 0L) + (if (it.format == "video") 1_000_000_000L else 0L) }
                                                }
                                            }
                                            .sortedByDescending { stream ->
                                                stream.quality.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
                                            }
                                    }
                                    val currentQualityRes = state.videoQuality?.takeWhile { it.isDigit() } ?: ""
                                    val currentIdxInVideo = videoStreams.indexOfFirst { it.quality.startsWith(currentQualityRes) }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxSize()
                                            .combinedClickable(
                                                enabled = videoStreams.isNotEmpty(),
                                                onClick = {
                                                    if (videoStreams.isNotEmpty()) {
                                                        var nextIdx = (currentIdxInVideo - 1 + videoStreams.size) % videoStreams.size
                                                        var attempts = 0
                                                        while (attempts < videoStreams.size) {
                                                            val candidateStream = videoStreams[nextIdx]
                                                            val candRes = candidateStream.quality.takeWhile { it.isDigit() }
                                                            val isCandDownloaded = state.completedDownloadQualities.any { it.takeWhile { c -> c.isDigit() } == candRes }
                                                            val isOnline = state.availableStreams.any { it.url.startsWith("http") }
                                                            if (isOnline || isCandDownloaded) {
                                                                val nextStreamIndex = state.availableStreams.indexOf(candidateStream)
                                                                if (nextStreamIndex != -1) {
                                                                    viewModel.changeStreamQuality(nextStreamIndex)
                                                                }
                                                                break
                                                            }
                                                            nextIdx = (nextIdx - 1 + videoStreams.size) % videoStreams.size
                                                            attempts++
                                                        }
                                                    }
                                                },
                                                onLongClick = { showQualityMenu = true }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_mn_sliders),
                                                contentDescription = "Video Quality",
                                                tint = MediaNestColors.Accent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = state.videoQuality ?: "Auto",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        if (videoStreams.isNotEmpty()) {
                                            DropdownMenu(
                                                expanded = showQualityMenu,
                                                onDismissRequest = { showQualityMenu = false }
                                            ) {
                                                videoStreams.forEach { streamSource ->
                                                    val streamRes = streamSource.quality.takeWhile { it.isDigit() }
                                                    val isDownloaded = state.completedDownloadQualities.any { it.takeWhile { c -> c.isDigit() } == streamRes }
                                                    val isSelected = remember(state.videoQuality, streamSource) {
                                                        val currentRes = state.videoQuality?.takeWhile { it.isDigit() } ?: ""
                                                        streamSource.quality.startsWith(currentRes)
                                                    }

                                                    val qualityLabel = if (!streamSource.codec.isNullOrEmpty()) {
                                                        "${streamSource.quality} (${streamSource.codec})"
                                                    } else {
                                                        streamSource.quality
                                                    }
                                                    DropdownMenuItem(
                                                        modifier = if (isSelected) {
                                                            Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                                        } else {
                                                            Modifier
                                                        },
                                                        text = {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                Text(
                                                                    text = qualityLabel,
                                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                                )
                                                                if (isDownloaded) {
                                                                    Icon(
                                                                        painter = painterResource(R.drawable.ic_mn_check_circle),
                                                                        contentDescription = "Downloaded",
                                                                        tint = MediaNestColors.Success,
                                                                        modifier = Modifier.size(16.dp)
                                                                    )
                                                                }
                                                            }
                                                        },
                                                        onClick = {
                                                            val targetIndex = state.availableStreams.indexOf(streamSource)
                                                            if (targetIndex != -1) {
                                                                viewModel.changeStreamQuality(targetIndex)
                                                            }
                                                            showQualityMenu = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (hasQueueContext) {
                                    // Divider between Quality (or Speed) and Queue
                                    androidx.compose.material3.VerticalDivider(
                                        modifier = Modifier.height(24.dp),
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                    )

                                    // Queue Selector
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxSize()
                                            .clickable { showQueueSheet = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_mn_playlist),
                                                contentDescription = "Up Next queue",
                                                tint = MediaNestColors.TextSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = if (currentQueue.isNotEmpty()) "Queue (${currentQueue.size})" else "Queue",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Normal,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                }

                // Floating Alerts Column
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    if (state.historyPositionMs > 5000L && state.historyPositionMs < state.durationMs - 10000L && showResumeButton) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.95f),
                            shape = MaterialTheme.shapes.small,
                            shadowElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Last watched: ${formatDuration(state.historyPositionMs)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 4.dp)
                                ) {
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
                                            painter = painterResource(R.drawable.ic_mn_close),
                                            contentDescription = "Dismiss",
                                            tint = MediaNestColors.TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                state.error?.let { errorMsg ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MediaNestColors.PlayerSurface.copy(alpha = 0.7f))
                            .clickable { viewModel.resetError() },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(errorMsg, color = MediaNestColors.TextPrimary)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { viewModel.retry() }) {
                                Text("Retry")
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("Tap to dismiss", color = MediaNestColors.TextSecondary)
                        }
                    }
                }
            }
        }
    }

    if (showQueueSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MediaNestColors.Raised,
            contentColor = MediaNestColors.TextPrimary,
            scrimColor = MediaNestColors.Scrim,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 4.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MediaNestColors.Border)
                )
            },
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                // Header: 'Up Next' + count + autoplay toggle inline
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mn_play),
                        contentDescription = null,
                        tint = MediaNestColors.Accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Up Next",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${currentQueue.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MediaNestColors.TextSecondary
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Autoplay",
                        style = MaterialTheme.typography.labelSmall,
                        color = MediaNestColors.TextSecondary
                    )
                    Spacer(Modifier.width(6.dp))
                    Switch(
                        checked = isAutoplayEnabled,
                        onCheckedChange = {
                            isAutoplayEnabled = it
                            onAutoplayToggle?.invoke(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MediaNestColors.Accent,
                            checkedTrackColor = MediaNestColors.Accent.copy(alpha = 0.5f)
                        )
                    )
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MediaNestColors.Border, thickness = 0.5.dp)
                Spacer(Modifier.height(8.dp))
                if (currentQueue.isEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nothing in the queue",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MediaNestColors.TextSecondary
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(currentQueue.size) { i ->
                            val item = currentQueue[i]
                            val idx = i
                            PlayerQueueItemRow(
                                item = item,
                                index = idx,
                                totalCount = currentQueue.size,
                                isCurrent = item.id == state.videoId,
                                onMoveUp = {
                                    if (idx > 0) {
                                        moveQueueItem(idx, idx - 1)
                                    }
                                },
                                onMoveDown = {
                                    if (idx < currentQueue.size - 1) {
                                        moveQueueItem(idx, idx + 1)
                                    }
                                },
                                onDragMove = { targetIdx -> moveQueueItem(idx, targetIdx) },
                                onClick = {
                                    if (onQueueItemClick != null) onQueueItemClick(item) else viewModel.initialize(
                                        item.id,
                                        item.streamIndex,
                                        item.downloadId
                                    )
                                },
                                onRemove = {
                                    currentQueue = currentQueue.toMutableList().also {
                                        val removed = it.removeAt(idx)
                                        onQueueReordered?.invoke(it)
                                        onRemoveFromQueue?.invoke(removed)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    return com.example.medianest.ui.utils.UiUtils.formatDuration(ms / 1000)
}

@Composable
fun AudioEqualizerBars(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "EqualizerTransition")

    val h1 by transition.animateFloat(
        initialValue = 4f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq1"
    )
    val h2 by transition.animateFloat(
        initialValue = 18f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550, delayMillis = 100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq2"
    )
    val h3 by transition.animateFloat(
        initialValue = 8f,
        targetValue = 24f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, delayMillis = 200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq3"
    )
    val h4 by transition.animateFloat(
        initialValue = 22f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, delayMillis = 150, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq4"
    )
    val h5 by transition.animateFloat(
        initialValue = 6f,
        targetValue = 16f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, delayMillis = 50, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq5"
    )

    val heights = if (isPlaying) {
        listOf(h1, h2, h3, h4, h5)
    } else {
        listOf(6f, 12f, 16f, 10f, 6f)
    }

    Row(
        modifier = modifier.height(24.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        heights.forEach { heightVal ->
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height(heightVal.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(MediaNestColors.Accent)
            )
        }
    }
}

@Composable
fun WatchCountDisplay(
    count: Int,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium
) {
    var previousCount by remember { mutableStateOf(count) }
    var scaleTrigger by remember { mutableStateOf(false) }

    LaunchedEffect(count) {
        if (count > previousCount) {
            scaleTrigger = true
            kotlinx.coroutines.delay(400)
            scaleTrigger = false
        }
        previousCount = count
    }

    val iconScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (scaleTrigger) 1.5f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "IconScale"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_mn_eye),
            contentDescription = "Watch Count",
            tint = iconColor,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
        )
        Spacer(Modifier.width(6.dp))
        
        AnimatedContent(
            targetState = count,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInVertically { height -> height } + fadeIn() togetherWith
                            slideOutVertically { height -> -height } + fadeOut())
                        .using(SizeTransform(clip = false))
                } else {
                    fadeIn() togetherWith fadeOut()
                }
            },
            label = "WatchCountAnimation"
        ) { targetCount ->
            Text(
                text = targetCount.toString(),
                color = textColor,
                style = style.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
fun AutoplayQueueHeader(
    contextTitle: String?,
    contextType: String?,
    queueSize: Int,
    isAutoplayEnabled: Boolean,
    isExpanded: Boolean,
    onAutoplayToggle: (Boolean) -> Unit,
    onExpandToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val contextIconRes = when (contextType?.lowercase()) {
                "playlist" -> R.drawable.ic_mn_playlist
                "folder" -> R.drawable.ic_mn_folder
                "favorites" -> R.drawable.ic_mn_heart
                else -> R.drawable.ic_mn_playlist
            }
            val defaultTitle = when (contextType?.lowercase()) {
                "playlist" -> "Playlist"
                "folder" -> "Folder"
                "favorites" -> "Favorites"
                else -> "Up Next Queue"
            }
            val titleText = contextTitle?.ifBlank { defaultTitle } ?: defaultTitle

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clickable { onExpandToggle() }
            ) {
                Icon(
                    painter = painterResource(contextIconRes),
                    contentDescription = null,
                    tint = if (contextType?.lowercase() == "favorites") MediaNestColors.Destructive else MediaNestColors.Accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (queueSize > 0) "$queueSize videos" else "Empty queue",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Autoplay next",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Switch(
                    checked = isAutoplayEnabled,
                    onCheckedChange = onAutoplayToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.graphicsLayer {
                        scaleX = 0.8f
                        scaleY = 0.8f
                    }
                )
                IconButton(
                    onClick = onExpandToggle,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(if (isExpanded) R.drawable.ic_mn_chevron_up else R.drawable.ic_mn_chevron_down),
                        contentDescription = if (isExpanded) "Collapse Queue" else "Expand Queue",
                        tint = MediaNestColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PlayerQueueItemRow(
    item: PlayerQueueItem,
    index: Int,
    totalCount: Int,
    isCurrent: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDragMove: (targetIndex: Int) -> Unit,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dragAccumulator by remember { mutableStateOf(0f) }
    val dragThresholdPx = 120f

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            }
        ),
        border = BorderStroke(
            width = if (isCurrent) 1.5.dp else 1.dp,
            color = if (isCurrent) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag handle with draggable modifier
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            dragAccumulator += delta
                            if (dragAccumulator > dragThresholdPx && index < totalCount - 1) {
                                onDragMove(index + 1)
                                dragAccumulator = 0f
                            } else if (dragAccumulator < -dragThresholdPx && index > 0) {
                                onDragMove(index - 1)
                                dragAccumulator = 0f
                            }
                        },
                        onDragStopped = {
                            dragAccumulator = 0f
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_mn_grip),
                    contentDescription = "Reorder Drag Handle",
                    tint = MediaNestColors.TextSecondary.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.width(4.dp))

            // Thumbnail with duration overlay
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (!item.thumbnailUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (item.durationSeconds > 0L) {
                    Surface(
                        color = MediaNestColors.PlayerSurface.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(2.dp)
                    ) {
                        Text(
                            text = UiUtils.formatDuration(item.durationSeconds),
                            style = MaterialTheme.typography.labelSmall,
                            color = MediaNestColors.TextPrimary,
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.width(10.dp))

            // Title & Channel on Row 1, Channel & Controls on Row 2
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 2.dp)
            ) {
                if (isCurrent) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(bottom = 2.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_mn_play),
                            contentDescription = null,
                            tint = MediaNestColors.Accent,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "NOW PLAYING",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Row 1: Full Title with persistent original index
                val itemNumber = item.originalIndex ?: (index + 1)
                Text(
                    text = if (item.title.isNotEmpty()) "$itemNumber. ${item.title}" else item.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 17.sp
                    ),
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(4.dp))

                // Row 2: Channel Name & Up / Down / Remove Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.channelName.isNotEmpty()) {
                        Text(
                            text = item.channelName,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }

                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                        Row(
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onMoveUp,
                                enabled = index > 0,
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_chevron_up),
                                    contentDescription = "Move Up",
                                    tint = if (index > 0) MediaNestColors.TextPrimary else MediaNestColors.TextSecondary.copy(alpha = 0.25f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = onMoveDown,
                                enabled = index < totalCount - 1,
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_chevron_down),
                                    contentDescription = "Move Down",
                                    tint = if (index < totalCount - 1) MediaNestColors.TextPrimary else MediaNestColors.TextSecondary.copy(alpha = 0.25f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = onRemove,
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mn_close),
                                    contentDescription = "Remove from Queue",
                                    tint = MediaNestColors.TextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
