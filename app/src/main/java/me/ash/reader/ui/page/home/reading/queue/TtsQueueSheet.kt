package me.ash.reader.ui.page.home.reading.queue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import me.ash.reader.R
import me.ash.reader.infrastructure.android.htmlSegmentCharCounts
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueueContentType
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueueItem
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueueMode
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueuePlaybackState
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueueState
import me.ash.reader.infrastructure.android.ttsqueue.TtsSleepTimerOption
import me.ash.reader.ui.component.base.RYDialog

private const val MS_PER_MINUTE = 60_000L

@Composable
private fun TtsNowPlayingCard(
    state: TtsQueueState,
    onOpenCurrentArticle: (String) -> Unit,
    onSeekCurrent: (Int) -> Unit,
    onPreviousSegment: () -> Unit,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onNextSegment: () -> Unit,
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (currentItem == null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text =
                            if (state.mode == TtsQueueMode.Commute) {
                                stringResource(id = R.string.commute_brief_empty_title)
                            } else {
                                stringResource(id = R.string.playlist_empty)
                            },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (state.mode == TtsQueueMode.Commute) {
                        Text(
                            text = stringResource(id = R.string.commute_brief_empty_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = currentItem.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { onOpenCurrentArticle(currentItem.articleId) },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text =
                                if (currentItem.contentType == TtsQueueContentType.AiSummary) {
                                    stringResource(id = R.string.commute_brief_summary_item, currentItem.feedName)
                                } else {
                                    currentItem.feedName
                                },
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
                    onPreviousArticle = onPrevious,
                    onTogglePlay = onTogglePlay,
                    onNextArticle = onNext,
                    onNextSegment = onNextSegment,
                )

                TtsPlaybackTimelineRow(
                    playbackState = state.playbackState,
                    currentSegmentIndex = state.currentSegmentIndex,
                    currentSegmentStartedAtMillis = state.currentSegmentStartedAtMillis,
                    currentSegmentDurationMs = state.currentSegmentDurationMs,
                    segmentCharCounts = state.currentSegmentCharCounts,
                    onSeekToSegment = onSeekCurrent,
                )
            }
        }
    }
}

@Composable
fun TtsQueueSheet(
    state: TtsQueueState,
    onSwitchMode: (TtsQueueMode) -> Unit,
    onGenerateCommuteBrief: () -> Unit,
    onPlayItem: (String) -> Unit,
    onPauseCurrent: () -> Unit,
    onSeekCurrent: (Int) -> Unit,
    onPreviousSegment: () -> Unit,
    onNextSegment: () -> Unit,
    onSetSleepTimer: (TtsSleepTimerOption) -> Unit,
    onOpenCurrentArticle: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRemove: (String) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRegenerateConfirm by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        QueueHeader(
            state = state,
            onSwitchMode = onSwitchMode,
            onGenerateCommuteBrief = {
                if (state.mode != TtsQueueMode.Commute || state.items.isEmpty()) {
                    onGenerateCommuteBrief()
                } else {
                    showRegenerateConfirm = true
                }
            },
            onClear = onClear,
            onSetSleepTimer = onSetSleepTimer,
        )

        TtsNowPlayingCard(
            state = state,
            onOpenCurrentArticle = onOpenCurrentArticle,
            onSeekCurrent = onSeekCurrent,
            onPreviousSegment = onPreviousSegment,
            onTogglePlay = {
                when (state.playbackState) {
                    TtsQueuePlaybackState.Reading -> onPauseCurrent()
                    TtsQueuePlaybackState.Preparing -> Unit
                    else -> state.currentArticleId?.let(onPlayItem)
                }
            },
            onPrevious = onPrevious,
            onNext = onNext,
            onNextSegment = onNextSegment,
        )

        if (state.items.isEmpty()) {
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.items, key = { "${it.contentType}-${it.articleId}" }) { item ->
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
                            text =
                                if (item.contentType == TtsQueueContentType.AiSummary) {
                                    stringResource(id = R.string.commute_brief_summary_item, item.feedName)
                                } else {
                                    item.feedName
                                },
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

    RegenerateCommuteBriefDialog(
        visible = showRegenerateConfirm,
        state = state,
        onConfirm = {
            showRegenerateConfirm = false
            onGenerateCommuteBrief()
        },
        onDismiss = { showRegenerateConfirm = false },
    )
}

@Composable
private fun QueueHeader(
    state: TtsQueueState,
    onSwitchMode: (TtsQueueMode) -> Unit,
    onGenerateCommuteBrief: () -> Unit,
    onClear: () -> Unit,
    onSetSleepTimer: (TtsSleepTimerOption) -> Unit,
) {
    var modeMenuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text =
                    if (state.mode == TtsQueueMode.Commute) {
                        stringResource(id = R.string.commute_brief_mode_title)
                    } else {
                        stringResource(id = R.string.playlist_mode_title)
                    },
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.clickable { modeMenuExpanded = true },
            )
            DropdownMenu(
                expanded = modeMenuExpanded,
                onDismissRequest = { modeMenuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(id = R.string.normal_mode)) },
                    onClick = {
                        modeMenuExpanded = false
                        onSwitchMode(TtsQueueMode.Normal)
                    },
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(id = R.string.commute_mode)) },
                    onClick = {
                        modeMenuExpanded = false
                        onSwitchMode(TtsQueueMode.Commute)
                    },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.mode == TtsQueueMode.Commute) {
                IconButton(onClick = onGenerateCommuteBrief) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription =
                            stringResource(
                                id =
                                    if (state.items.isEmpty()) {
                                        R.string.generate_commute_brief
                                    } else {
                                        R.string.regenerate_commute_brief
                                    },
                            ),
                    )
                }
            }
            TtsSleepTimerDropdown(
                sleepTimer = state.sleepTimer,
                enabled = state.currentItem != null,
                onSelect = onSetSleepTimer,
            )
            TextButton(
                onClick = onClear,
                enabled = state.items.isNotEmpty(),
            ) {
                Text(text = stringResource(id = R.string.clear))
            }
        }
    }
}

@Composable
private fun RegenerateCommuteBriefDialog(
    state: TtsQueueState,
    visible: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val stats = remember(state.items, state.commuteMeta) { state.collectCommuteBriefStats() }
    RYDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.regenerate_commute_brief_title)) },
        text = {
            Text(
                text =
                    if (stats != null && stats.targetDurationMinutes != null) {
                        stringResource(
                            id = R.string.regenerate_commute_brief_desc_with_stats_and_target,
                            formatCommuteGeneratedDateTime(stats.generatedAtMillis),
                            stats.itemCount,
                            formatCount(stats.totalChars),
                            stats.estimatedDurationMinutes,
                            stats.targetDurationMinutes,
                        )
                    } else if (stats != null) {
                        stringResource(
                            id = R.string.regenerate_commute_brief_desc_with_stats,
                            formatCommuteGeneratedDateTime(stats.generatedAtMillis),
                            stats.itemCount,
                            formatCount(stats.totalChars),
                            stats.estimatedDurationMinutes,
                        )
                    } else {
                        stringResource(id = R.string.regenerate_commute_brief_desc)
                    }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(id = R.string.replace_and_generate))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.cancel))
            }
        },
    )
}

private data class CommuteBriefStats(
    val generatedAtMillis: Long,
    val itemCount: Int,
    val totalChars: Int,
    val estimatedDurationMinutes: Int,
    val targetDurationMinutes: Int?,
)

private fun TtsQueueState.collectCommuteBriefStats(): CommuteBriefStats? {
    val meta = commuteMeta
    val itemCount = meta?.itemCount ?: items.size
    val totalChars =
        items.sumOf { item ->
            val html =
                item.summaryHtmlContent?.takeIf(String::isNotBlank)
                    ?: item.htmlContent?.takeIf(String::isNotBlank)
                    ?: return@sumOf 0
            htmlSegmentCharCounts(html).sum()
        }
    val estimatedDurationMinutes =
        meta?.estimatedDurationMinutes
            ?: ((items.sumOf { item -> item.estimatedDurationMs ?: 0L }.toDouble() / MS_PER_MINUTE)
                .roundToInt()
                .coerceAtLeast(if (items.isEmpty()) 0 else 1))
    val generatedAtMillis = meta?.generatedAtMillis ?: return null
    return CommuteBriefStats(
        generatedAtMillis = generatedAtMillis,
        itemCount = itemCount,
        totalChars = totalChars,
        estimatedDurationMinutes = estimatedDurationMinutes,
        targetDurationMinutes = meta.targetDurationMinutes,
    )
}

private fun formatCommuteGeneratedDateTime(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun formatCount(count: Int): String = NumberFormat.getIntegerInstance(Locale.getDefault()).format(count)
