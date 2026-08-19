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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import com.example.medianest.R
import com.example.medianest.ui.theme.MediaNestColors
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.medianest.data.local.entity.SubscriptionEntity
import com.example.medianest.data.preferences.SubscriptionsPreferences
import com.example.medianest.ui.components.GlassCard
import com.example.medianest.ui.components.EndOfListIndicator
import com.example.medianest.ui.viewmodel.SubscriptionsViewModel
import com.example.medianest.ui.viewmodel.ViewMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    sourceType: String,
    searchQuery: String = "",
    viewMode: ViewMode = ViewMode.LIST,
    onSubscriptionClick: (String, String) -> Unit,
    viewModel: SubscriptionsViewModel = hiltViewModel(),
    subscriptionsPreferences: SubscriptionsPreferences? = null
) {
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs = remember(context, subscriptionsPreferences) {
        subscriptionsPreferences ?: SubscriptionsPreferences(context.applicationContext)
    }
    val showShorts by prefs.showShorts.collectAsStateWithLifecycle(initialValue = SubscriptionsPreferences.DEFAULT_SHOW_SHORTS)
    var pendingUnsubscribe by remember { mutableStateOf<SubscriptionEntity?>(null) }

    val filtered = subscriptions.filter { 
        it.sourceType == sourceType && 
        (searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Show Shorts filter toggle header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Show Shorts",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = showShorts,
                    onCheckedChange = { checked ->
                        coroutineScope.launch {
                            prefs.setShowShorts(checked)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MediaNestColors.AccentDeep,
                        checkedThumbColor = Color.White,
                        uncheckedTrackColor = MediaNestColors.ProgressTrack,
                        uncheckedThumbColor = Color.White,
                        uncheckedBorderColor = MediaNestColors.Border
                    ),
                    modifier = Modifier.scale(0.8f)
                )
            }

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No results found" else "No subscriptions yet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                if (viewMode == ViewMode.GRID) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filtered, key = { it.id }) { sub ->
                            SubscriptionCard(
                                subscription = sub,
                                onAutoDownloadChange = { autoDownload, audioOnly ->
                                    viewModel.updateAutoDownload(sub.id, autoDownload, audioOnly)
                                    coroutineScope.launch {
                                        val msg = when {
                                            autoDownload != sub.autoDownload && autoDownload -> "Auto-download enabled for ${sub.name}"
                                            autoDownload != sub.autoDownload && !autoDownload -> "Auto-download disabled for ${sub.name}"
                                            audioOnly != sub.audioOnly && audioOnly -> "Audio-only auto-download enabled for ${sub.name}"
                                            else -> "Audio-only auto-download disabled for ${sub.name}"
                                        }
                                        snackbarHostState.showSnackbar(msg)
                                    }
                                },
                                onUnsubscribe = { pendingUnsubscribe = sub },
                                onClick = { onSubscriptionClick(sub.sourceType, sub.sourceId) }
                            )
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "*Automatically downloads new uploads*",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontStyle = FontStyle.Italic
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EndOfListIndicator()
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filtered, key = { it.id }) { sub ->
                            SubscriptionCard(
                                subscription = sub,
                                onAutoDownloadChange = { autoDownload, audioOnly ->
                                    viewModel.updateAutoDownload(sub.id, autoDownload, audioOnly)
                                    coroutineScope.launch {
                                        val msg = when {
                                            autoDownload != sub.autoDownload && autoDownload -> "Auto-download enabled for ${sub.name}"
                                            autoDownload != sub.autoDownload && !autoDownload -> "Auto-download disabled for ${sub.name}"
                                            audioOnly != sub.audioOnly && audioOnly -> "Audio-only auto-download enabled for ${sub.name}"
                                            else -> "Audio-only auto-download disabled for ${sub.name}"
                                        }
                                        snackbarHostState.showSnackbar(msg)
                                    }
                                },
                                onUnsubscribe = { pendingUnsubscribe = sub },
                                onClick = { onSubscriptionClick(sub.sourceType, sub.sourceId) }
                            )
                        }
                        item {
                            Text(
                                text = "*Automatically downloads new uploads*",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontStyle = FontStyle.Italic
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                        item {
                            EndOfListIndicator()
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        )

        pendingUnsubscribe?.let { sub ->
            AlertDialog(
                onDismissRequest = { pendingUnsubscribe = null },
                title = {
                    Text(
                        text = "Remove?",
                        color = MediaNestColors.TextPrimary
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to remove \"${sub.name}\"?",
                        color = MediaNestColors.TextSecondary
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val subToRemove = sub
                            pendingUnsubscribe = null
                            viewModel.unsubscribe(subToRemove.id)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Unsubscribed from ${subToRemove.name}")
                            }
                        }
                    ) {
                        Text(
                            text = "Remove",
                            color = MediaNestColors.Destructive
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingUnsubscribe = null }) {
                        Text(
                            text = "Cancel",
                            color = MediaNestColors.TextSecondary
                        )
                    }
                },
                containerColor = MediaNestColors.Raised
            )
        }
    }
}

@Composable
private fun SubscriptionCard(
    subscription: SubscriptionEntity,
    onAutoDownloadChange: (Boolean, Boolean) -> Unit,
    onUnsubscribe: () -> Unit,
    onClick: () -> Unit
) {
    var isTitleExpanded by remember { mutableStateOf(false) }

    GlassCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = subscription.thumbnailUrl,
                    contentDescription = subscription.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(if (subscription.sourceType == "playlist") RoundedCornerShape(8.dp) else CircleShape)
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subscription.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = if (isTitleExpanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { isTitleExpanded = !isTitleExpanded }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Auto-download",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Switch(
                            checked = subscription.autoDownload,
                            onCheckedChange = { onAutoDownloadChange(it, subscription.audioOnly) },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MediaNestColors.AccentDeep,
                                checkedThumbColor = Color.White,
                                uncheckedTrackColor = MediaNestColors.ProgressTrack,
                                uncheckedThumbColor = Color.White,
                                uncheckedBorderColor = MediaNestColors.Border
                            ),
                            modifier = Modifier.scale(0.8f)
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Audio only",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Switch(
                            checked = subscription.audioOnly,
                            onCheckedChange = { onAutoDownloadChange(subscription.autoDownload, it) },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MediaNestColors.AccentDeep,
                                checkedThumbColor = Color.White,
                                uncheckedTrackColor = MediaNestColors.ProgressTrack,
                                uncheckedThumbColor = Color.White,
                                uncheckedBorderColor = MediaNestColors.Border
                            ),
                            modifier = Modifier.scale(0.8f)
                        )
                    }
                }
                IconButton(onClick = onUnsubscribe) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mn_trash),
                        contentDescription = if (subscription.sourceType == "playlist") "Delete playlist" else "Unsubscribe",
                        tint = MediaNestColors.Destructive
                    )
                }
            }
        }
    }
}
