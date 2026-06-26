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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.medianest.data.local.entity.SubscriptionEntity
import com.example.medianest.ui.viewmodel.SubscriptionsViewModel
import com.example.medianest.ui.viewmodel.ViewMode
import com.example.medianest.ui.components.GlassCard
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    sourceType: String,
    searchQuery: String = "",
    viewMode: ViewMode = ViewMode.LIST,
    onSubscriptionClick: (String, String) -> Unit,
    viewModel: SubscriptionsViewModel = hiltViewModel()
) {
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val snackbarHostState = androidx.compose.runtime.remember { SnackbarHostState() }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val filtered = subscriptions.filter { 
        it.sourceType == sourceType && 
        (searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true))
    }

    if (filtered.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (searchQuery.isNotEmpty()) "No results found" else "No subscriptions yet",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        if (viewMode == ViewMode.GRID) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filtered, key = { it.id }) { sub ->
                    SubscriptionCard(
                        subscription = sub,
                        onAutoDownloadChange = { autoDownload, audioOnly ->
                            viewModel.updateAutoDownload(sub.id, autoDownload, audioOnly)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    if (autoDownload) "Auto-download enabled for ${sub.name}" else "Auto-download disabled for ${sub.name}"
                                )
                            }
                        },
                        onUnsubscribe = { 
                            viewModel.unsubscribe(sub.id) 
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Unsubscribed from ${sub.name}")
                            }
                        },
                        onClick = { onSubscriptionClick(sub.sourceType, sub.sourceId) }
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "*Automatically downloads new uploads*",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filtered, key = { it.id }) { sub ->
                    SubscriptionCard(
                        subscription = sub,
                        onAutoDownloadChange = { autoDownload, audioOnly ->
                            viewModel.updateAutoDownload(sub.id, autoDownload, audioOnly)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    if (autoDownload) "Auto-download enabled for ${sub.name}" else "Auto-download disabled for ${sub.name}"
                                )
                            }
                        },
                        onUnsubscribe = { 
                            viewModel.unsubscribe(sub.id) 
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Unsubscribed from ${sub.name}")
                            }
                        },
                        onClick = { onSubscriptionClick(sub.sourceType, sub.sourceId) }
                    )
                }
                item {
                    Text(
                        text = "*Automatically downloads new uploads*",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
        
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.padding(bottom = 80.dp))
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Auto-download",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    androidx.compose.material3.Switch(
                        checked = subscription.autoDownload,
                        onCheckedChange = { onAutoDownloadChange(it, subscription.audioOnly) },
                        modifier = Modifier.scale(0.8f)
                    )
                }
                IconButton(onClick = onUnsubscribe) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = if (subscription.sourceType == "playlist") "Delete playlist" else "Unsubscribe",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
