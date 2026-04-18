package me.ash.reader.ui.page.home.reading.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal fun canSeekSegments(segmentCharCounts: List<Int>): Boolean = segmentCharCounts.size > 1

internal fun weightedProgressFraction(
    currentSegmentIndex: Int,
    segmentCharCounts: List<Int>,
    currentSegmentProgressFraction: Float = 0f,
): Float {
    if (!canSeekSegments(segmentCharCounts)) return 0f
    val totalChars = segmentCharCounts.sum().coerceAtLeast(1)
    val consumedChars = segmentCharCounts.take(currentSegmentIndex.coerceAtLeast(0)).sum()
    val currentSegmentChars = segmentCharCounts.getOrElse(currentSegmentIndex.coerceAtLeast(0)) { 0 }
    val currentChars = currentSegmentChars * currentSegmentProgressFraction.coerceIn(0f, 1f)
    return (consumedChars.toFloat() + currentChars) / totalChars.toFloat()
}

internal fun weightedSegmentIndexFromFraction(
    fraction: Float,
    segmentCharCounts: List<Int>,
): Int {
    if (!canSeekSegments(segmentCharCounts)) return 0
    val totalChars = segmentCharCounts.sum().coerceAtLeast(1)
    val targetChars = (fraction.coerceIn(0f, 1f) * totalChars).roundToInt()
    var accumulated = 0
    segmentCharCounts.forEachIndexed { index, count ->
        accumulated += count
        if (targetChars <= accumulated) return index
    }
    return segmentCharCounts.lastIndex
}

@Composable
internal fun TtsPlaybackProgressBar(
    currentSegmentIndex: Int,
    segmentCharCounts: List<Int>,
    onSeekToSegment: (Int) -> Unit,
    modifier: Modifier = Modifier,
    currentSegmentProgressFraction: Float = 0f,
) {
    var isDragging by remember { mutableStateOf(false) }
    var draftFraction by remember(segmentCharCounts) { mutableFloatStateOf(0f) }
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val actualFraction =
        weightedProgressFraction(
            currentSegmentIndex = currentSegmentIndex,
            segmentCharCounts = segmentCharCounts,
            currentSegmentProgressFraction = currentSegmentProgressFraction,
        )
    val thumbSize = 12.dp
    val thumbRadiusPx = with(LocalDensity.current) { (thumbSize / 2).roundToPx() }

    LaunchedEffect(actualFraction, isDragging) {
        if (!isDragging) draftFraction = actualFraction
    }

    val seekEnabled = canSeekSegments(segmentCharCounts)
    val displayedFraction = if (isDragging) draftFraction else actualFraction

    val thumbOffsetPx =
        ((displayedFraction * trackWidthPx).roundToInt() - thumbRadiusPx)
            .coerceIn(0, (trackWidthPx.roundToInt() - thumbRadiusPx * 2).coerceAtLeast(0))

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(24.dp)
                .onSizeChanged { trackWidthPx = it.width.toFloat() }
                .pointerInput(seekEnabled, trackWidthPx, segmentCharCounts) {
                    if (!seekEnabled || trackWidthPx <= 0f) return@pointerInput
                    detectTapGestures { offset: Offset ->
                        onSeekToSegment(
                            weightedSegmentIndexFromFraction(
                                fraction = offset.x / trackWidthPx,
                                segmentCharCounts = segmentCharCounts,
                            ),
                        )
                    }
                }
                .draggable(
                    state =
                        rememberDraggableState { delta ->
                            if (!seekEnabled || trackWidthPx <= 0f) return@rememberDraggableState
                            isDragging = true
                            draftFraction = (draftFraction + delta / trackWidthPx).coerceIn(0f, 1f)
                        },
                    orientation = Orientation.Horizontal,
                    enabled = seekEnabled,
                    onDragStopped = {
                        isDragging = false
                        onSeekToSegment(
                            weightedSegmentIndexFromFraction(
                                fraction = draftFraction,
                                segmentCharCounts = segmentCharCounts,
                            ),
                        )
                    },
                ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(displayedFraction.coerceIn(0f, 1f))
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.primary),
        )
        Box(
            modifier =
                Modifier
                    .offset {
                        IntOffset(
                            x = thumbOffsetPx,
                            y = 0,
                        )
                    }
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .align(Alignment.CenterStart),
        )
    }
}
