package me.ash.reader.ui.page.home.reading.queue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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

@Composable
private fun TtsNowPlayingCard(
    state: TtsQueueState,
    onOpenCurrentArticle: (String) -> Unit,
    onSeekCurrent: (Int) -> Unit,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val currentItem = state.currentItem
    val playbackControlEnabled =
        currentItem != null && state.playbackState != TtsQueuePlaybackState.Preparing

    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (currentItem == null) {
                Text(
                    text = stringResource(id = R.string.playlist_empty),
                    style = MaterialTheme.typography.titleMedium,
                )
            } else {
                Column {
                    Text(
                        text = "${(state.currentIndex ?: 0) + 1}/${state.items.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Column(
                        modifier =
                            Modifier.clickable {
                                onOpenCurrentArticle(currentItem.articleId)
                            },
                    ) {
                        Text(
                            text = currentItem.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = currentItem.feedName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                TtsPlaybackProgressBar(
                    currentSegmentIndex = state.currentSegmentIndex,
                    segmentCharCounts = state.currentSegmentCharCounts,
                    onSeekToSegment = onSeekCurrent,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onPrevious,
                        enabled = playbackControlEnabled,
                        modifier = Modifier.size(52.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = null,
                        )
                    }
                    IconButton(
                        onClick = onTogglePlay,
                        enabled = playbackControlEnabled,
                        modifier = Modifier.size(60.dp),
                    ) {
                        Icon(
                            imageVector =
                                if (state.playbackState == TtsQueuePlaybackState.Reading) {
                                    Icons.Rounded.Pause
                                } else {
                                    Icons.Rounded.PlayArrow
                                },
                            contentDescription = null,
                        )
                    }
                    IconButton(
                        onClick = onNext,
                        enabled = playbackControlEnabled,
                        modifier = Modifier.size(52.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TtsQueueSheet(
    state: TtsQueueState,
    onPlayItem: (String) -> Unit,
    onPauseCurrent: () -> Unit,
    onSeekCurrent: (Int) -> Unit,
    onOpenCurrentArticle: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
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

        TtsNowPlayingCard(
            state = state,
            onOpenCurrentArticle = onOpenCurrentArticle,
            onSeekCurrent = onSeekCurrent,
            onTogglePlay = {
                when (state.playbackState) {
                    TtsQueuePlaybackState.Reading -> onPauseCurrent()
                    TtsQueuePlaybackState.Preparing -> Unit
                    else -> state.currentArticleId?.let(onPlayItem)
                }
            },
            onPrevious = onPrevious,
            onNext = onNext,
        )

        if (state.items.isEmpty()) {
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.items, key = TtsQueueItem::articleId) { item ->
                val isCurrent = item.articleId == state.currentArticleId
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .clickable { onPlayItem(item.articleId) }
                                .padding(end = 8.dp),
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
