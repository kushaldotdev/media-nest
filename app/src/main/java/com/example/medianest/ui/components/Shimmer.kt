package com.example.medianest.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.medianest.ui.theme.MediaNestColors
import com.example.medianest.ui.theme.MediaNestShapes

/**
 * Shimmer effect modifier for loading skeleton placeholders.
 */
fun Modifier.shimmerBackground(shape: Shape? = null): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val translateAnim = transition.animateFloat(
        initialValue = -300f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_anim"
    )

    val shimmerColors = listOf(
        MediaNestColors.Raised.copy(alpha = 0.5f),
        MediaNestColors.Card.copy(alpha = 0.85f),
        MediaNestColors.Raised.copy(alpha = 0.5f)
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim.value - 300f, translateAnim.value - 300f),
        end = Offset(translateAnim.value, translateAnim.value)
    )

    if (shape != null) {
        this.clip(shape).background(brush)
    } else {
        this.background(brush)
    }
}

/**
 * Grid-style Video Card Skeleton.
 */
@Composable
fun VideoCardSkeleton(modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Thumbnail placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .shimmerBackground(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.height(10.dp))
            // Title line 1
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(14.dp)
                    .shimmerBackground(RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.height(6.dp))
            // Title line 2
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(14.dp)
                    .shimmerBackground(RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Channel & metadata
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .shimmerBackground(CircleShape)
                )
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(11.dp)
                        .shimmerBackground(RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

/**
 * Row / List-style Video Card Skeleton.
 */
@Composable
fun VideoRowSkeleton(modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail placeholder
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .aspectRatio(16f / 9f)
                    .shimmerBackground(RoundedCornerShape(8.dp))
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Title line 1
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .height(14.dp)
                        .shimmerBackground(RoundedCornerShape(4.dp))
                )
                // Title line 2
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(14.dp)
                        .shimmerBackground(RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.height(2.dp))
                // Metadata subtitle
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.45f)
                        .height(11.dp)
                        .shimmerBackground(RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

/**
 * Generic Grid Skeleton layout for Collections, Home, Playlists, etc.
 */
@Composable
fun VideoGridSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 6,
    contentPadding: PaddingValues = PaddingValues(8.dp)
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat((count + 1) / 2) { rowIndex ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VideoCardSkeleton(modifier = Modifier.weight(1f))
                if (rowIndex * 2 + 1 < count) {
                    VideoCardSkeleton(modifier = Modifier.weight(1f))
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Generic List Skeleton layout for Collections, Downloads, History, etc.
 */
@Composable
fun VideoListSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 6,
    contentPadding: PaddingValues = PaddingValues(8.dp)
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(count) {
            VideoRowSkeleton()
        }
    }
}

/**
 * Skeleton for Channel Subscriptions cards.
 */
@Composable
fun SubscriptionCardSkeleton(modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .shimmerBackground(CircleShape)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(16.dp)
                        .shimmerBackground(RoundedCornerShape(4.dp))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(12.dp)
                        .shimmerBackground(RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

/**
 * Skeleton list for Subscription screen.
 */
@Composable
fun SubscriptionListSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 6,
    contentPadding: PaddingValues = PaddingValues(8.dp)
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(count) {
            SubscriptionCardSkeleton()
        }
    }
}

/**
 * Skeleton for Folder cards.
 */
@Composable
fun FolderCardSkeleton(modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .shimmerBackground(CircleShape)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(16.dp)
                    .shimmerBackground(RoundedCornerShape(4.dp))
            )
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(14.dp)
                    .shimmerBackground(RoundedCornerShape(4.dp))
            )
        }
    }
}

/**
 * Grid Skeleton for Folders.
 */
@Composable
fun FolderSkeletonGrid(
    modifier: Modifier = Modifier,
    count: Int = 6,
    contentPadding: PaddingValues = PaddingValues(8.dp)
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat((count + 1) / 2) { rowIndex ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FolderCardSkeleton(modifier = Modifier.weight(1f))
                if (rowIndex * 2 + 1 < count) {
                    FolderCardSkeleton(modifier = Modifier.weight(1f))
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
