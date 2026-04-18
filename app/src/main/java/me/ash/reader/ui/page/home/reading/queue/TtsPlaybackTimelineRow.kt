package me.ash.reader.ui.page.home.reading.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.ash.reader.infrastructure.android.ttsqueue.formatMsToTime
import me.ash.reader.infrastructure.android.ttsqueue.segmentCharCountsToDurationEstimate

@Composable
internal fun TtsPlaybackTimelineRow(
    currentSegmentIndex: Int,
    segmentCharCounts: List<Int>,
    onSeekToSegment: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val durationEstimate =
        segmentCharCountsToDurationEstimate(
            currentSegmentIndex = currentSegmentIndex,
            segmentCharCounts = segmentCharCounts,
        ) ?: return

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatMsToTime(durationEstimate.currentMs),
            modifier = Modifier.widthIn(min = 36.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TtsPlaybackProgressBar(
            currentSegmentIndex = currentSegmentIndex,
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
