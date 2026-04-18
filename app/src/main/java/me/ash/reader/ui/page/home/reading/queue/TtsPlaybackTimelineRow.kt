package me.ash.reader.ui.page.home.reading.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueuePlaybackState
import me.ash.reader.infrastructure.android.ttsqueue.formatMsToTime
import me.ash.reader.infrastructure.android.ttsqueue.segmentCharCountsToDurationEstimate

@Composable
internal fun TtsPlaybackTimelineRow(
    playbackState: TtsQueuePlaybackState,
    currentSegmentIndex: Int,
    currentSegmentStartedAtMillis: Long?,
    currentSegmentDurationMs: Long,
    segmentCharCounts: List<Int>,
    onSeekToSegment: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val durationEstimate =
        segmentCharCountsToDurationEstimate(
            currentSegmentIndex = currentSegmentIndex,
            segmentCharCounts = segmentCharCounts,
        ) ?: return
    val nowMillis = produceState(initialValue = System.currentTimeMillis(), playbackState, currentSegmentStartedAtMillis, currentSegmentDurationMs) {
        value = System.currentTimeMillis()
        if (playbackState == TtsQueuePlaybackState.Reading && currentSegmentStartedAtMillis != null && currentSegmentDurationMs > 0) {
            while (true) {
                delay(250)
                value = System.currentTimeMillis()
            }
        }
    }.value
    val currentSegmentProgressFraction =
        if (currentSegmentStartedAtMillis != null && currentSegmentDurationMs > 0) {
            val elapsedMs = (nowMillis - currentSegmentStartedAtMillis).coerceAtLeast(0L)
            (elapsedMs.toFloat() / currentSegmentDurationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    val currentTimeMs =
        (durationEstimate.currentMs + (currentSegmentDurationMs * currentSegmentProgressFraction).toLong())
            .coerceAtMost(durationEstimate.totalMs)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatMsToTime(currentTimeMs),
            modifier = Modifier.widthIn(min = 36.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TtsPlaybackProgressBar(
            currentSegmentIndex = currentSegmentIndex,
            currentSegmentProgressFraction = currentSegmentProgressFraction,
            segmentCharCounts = segmentCharCounts,
            onSeekToSegment = onSeekToSegment,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatMsToTime(durationEstimate.totalMs),
            modifier = Modifier.widthIn(min = 36.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
    }
}
