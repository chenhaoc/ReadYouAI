package me.ash.reader.ui.page.home.reading.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.ash.reader.R
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueueItem
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueuePlaybackState
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueueState

internal enum class TtsQueueItemControl {
    Play,
    Pause,
}

internal fun resolveQueueItemControl(
    state: TtsQueueState,
    articleId: String,
): TtsQueueItemControl =
    if (
        state.currentArticleId == articleId &&
        state.playbackState == TtsQueuePlaybackState.Reading
    ) {
        TtsQueueItemControl.Pause
    } else {
        TtsQueueItemControl.Play
    }

@Composable
fun TtsQueueSheet(
    state: TtsQueueState,
    onPlayItem: (String) -> Unit,
    onPauseCurrent: () -> Unit,
    onRemove: (String) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = stringResource(id = R.string.playlist),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(id = R.string.playlist_count, state.items.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = onClear,
                enabled = state.items.isNotEmpty(),
            ) {
                Text(text = stringResource(id = R.string.clear))
            }
        }

        if (state.items.isEmpty()) {
            Text(
                text = stringResource(id = R.string.playlist_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.items, key = TtsQueueItem::articleId) { item ->
                val isCurrent = item.articleId == state.currentArticleId
                val control = resolveQueueItemControl(state = state, articleId = item.articleId)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier =
                            Modifier.weight(1f).padding(end = 8.dp),
                    ) {
                        Text(
                            text = item.title,
                            style =
                                if (isCurrent) {
                                    MaterialTheme.typography.titleMedium
                                } else {
                                    MaterialTheme.typography.bodyLarge
                                },
                            color =
                                if (isCurrent) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = item.feedName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(
                        onClick = {
                            when (control) {
                                TtsQueueItemControl.Play -> onPlayItem(item.articleId)
                                TtsQueueItemControl.Pause -> onPauseCurrent()
                            }
                        }
                    ) {
                        Icon(
                            imageVector =
                                when (control) {
                                    TtsQueueItemControl.Play -> Icons.Rounded.PlayArrow
                                    TtsQueueItemControl.Pause -> Icons.Rounded.Pause
                                },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = { onMoveUp(item.articleId) }) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowUpward,
                            contentDescription = null,
                        )
                    }
                    IconButton(onClick = { onMoveDown(item.articleId) }) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowDownward,
                            contentDescription = null,
                        )
                    }
                    IconButton(onClick = { onRemove(item.articleId) }) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
