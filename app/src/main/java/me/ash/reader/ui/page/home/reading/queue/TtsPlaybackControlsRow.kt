package me.ash.reader.ui.page.home.reading.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueuePlaybackState

@Composable
internal fun TtsPlaybackControlsRow(
    playbackState: TtsQueuePlaybackState,
    controlEnabled: Boolean,
    canSkipToPreviousSegment: Boolean,
    canSkipToNextSegment: Boolean,
    onPreviousSegment: () -> Unit,
    onPreviousArticle: () -> Unit,
    onTogglePlay: () -> Unit,
    onNextArticle: () -> Unit,
    onNextSegment: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedIconButton(
            onClick = onPreviousSegment,
            enabled = controlEnabled && canSkipToPreviousSegment,
            modifier = Modifier.size(34.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.FastRewind,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onPreviousArticle,
            enabled = controlEnabled,
            modifier = Modifier.size(54.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = null,
                modifier = Modifier.size(34.dp),
            )
        }
        FilledIconButton(
            onClick = onTogglePlay,
            enabled = controlEnabled,
            modifier = Modifier.size(64.dp),
        ) {
            Icon(
                imageVector =
                    if (playbackState == TtsQueuePlaybackState.Reading) {
                        Icons.Rounded.Pause
                    } else {
                        Icons.Rounded.PlayArrow
                    },
                contentDescription = null,
                modifier = Modifier.size(34.dp),
            )
        }
        IconButton(
            onClick = onNextArticle,
            enabled = controlEnabled,
            modifier = Modifier.size(54.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = null,
                modifier = Modifier.size(30.dp),
            )
        }
        OutlinedIconButton(
            onClick = onNextSegment,
            enabled = controlEnabled && canSkipToNextSegment,
            modifier = Modifier.size(34.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.FastForward,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
