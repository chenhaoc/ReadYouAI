package me.ash.reader.ui.page.home.reading.queue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueuePlaybackState
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueueState

@Composable
fun TtsMiniPlayer(
    state: TtsQueueState,
    onTogglePlay: () -> Unit,
    onSeekToSegment: (Int) -> Unit,
    onPreviousSegment: () -> Unit,
    onPreviousArticle: () -> Unit,
    onNextArticle: () -> Unit,
    onNextSegment: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentItem = state.currentItem ?: return
    val playbackControlEnabled = state.playbackState != TtsQueuePlaybackState.Preparing

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier.clickable(onClick = onOpenQueue),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = currentItem.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = currentItem.feedName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        text = "${(state.currentIndex ?: 0) + 1}/${state.items.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            TtsPlaybackControlsRow(
                playbackState = state.playbackState,
                controlEnabled = playbackControlEnabled,
                canSkipToPreviousSegment = state.hasPreviousSegment,
                canSkipToNextSegment = state.hasNextSegment,
                onPreviousSegment = onPreviousSegment,
                onPreviousArticle = onPreviousArticle,
                onTogglePlay = onTogglePlay,
                onNextArticle = onNextArticle,
                onNextSegment = onNextSegment,
            )

            TtsPlaybackTimelineRow(
                playbackState = state.playbackState,
                currentSegmentIndex = state.currentSegmentIndex,
                currentSegmentStartedAtMillis = state.currentSegmentStartedAtMillis,
                currentSegmentDurationMs = state.currentSegmentDurationMs,
                segmentCharCounts = state.currentSegmentCharCounts,
                onSeekToSegment = onSeekToSegment,
            )
        }
    }
}
