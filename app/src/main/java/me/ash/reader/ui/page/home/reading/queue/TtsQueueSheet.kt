package me.ash.reader.ui.page.home.reading.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import me.ash.reader.R
import me.ash.reader.infrastructure.android.htmlSegmentCharCounts
import me.ash.reader.infrastructure.android.ttsqueue.TtsCommuteQueueGenerationMode
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueueContentType
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueueItem
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueueMode
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueuePlaybackState
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueueState
import me.ash.reader.infrastructure.android.ttsqueue.TtsSleepTimerOption
import me.ash.reader.ui.component.base.RYDialog

private const val MS_PER_MINUTE = 60_000L
private const val MODE_SWITCH_AFTER_MENU_CLOSE_DELAY_MS = 120L
private const val QUEUE_SHEET_HORIZONTAL_PADDING_DP = 14

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
    onToggleCurrentStarred: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentItem = state.currentItem
    val playbackControlEnabled =
        currentItem != null && state.playbackState != TtsQueuePlaybackState.Preparing

    Card(
        modifier = modifier,
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = currentItem.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .clickable { onOpenCurrentArticle(currentItem.articleId) },
                        )
                        Box(
                            modifier =
                                Modifier
                                    .size(32.dp)
                                    .clickable(onClick = onToggleCurrentStarred),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Icon(
                                modifier = Modifier.size(22.dp),
                                imageVector =
                                    if (state.currentItemStarred) {
                                        Icons.Rounded.Star
                                    } else {
                                        Icons.Rounded.StarOutline
                                    },
                                contentDescription =
                                    stringResource(
                                        if (state.currentItemStarred) {
                                            R.string.mark_as_unstar
                                        } else {
                                            R.string.mark_as_starred
                                        }
                                    ),
                                tint =
                                    if (state.currentItemStarred) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = currentItem.queueMetadataText(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(
                            text = "${(state.currentIndex ?: 0) + 1}/${state.items.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
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
                    segmentButtonSize = 30.dp,
                    articleButtonSize = 62.dp,
                    articleIconSize = 36.dp,
                    outerButtonSpacing = 16.dp,
                    innerButtonSpacing = 6.dp,
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
    commuteBuildGenerationMode: TtsCommuteQueueGenerationMode?,
    onSwitchMode: (TtsQueueMode) -> Unit,
    onGenerateCommuteBrief: (TtsCommuteQueueGenerationMode) -> Unit,
    onPlayItem: (String) -> Unit,
    onPauseCurrent: () -> Unit,
    onSeekCurrent: (Int) -> Unit,
    onPreviousSegment: () -> Unit,
    onNextSegment: () -> Unit,
    onSetSleepTimer: (TtsSleepTimerOption) -> Unit,
    onOpenCurrentArticle: (String) -> Unit,
    onToggleCurrentStarred: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRemove: (String) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showGenerateDialog by remember { mutableStateOf(false) }
    var pendingGenerationMode by remember { mutableStateOf<TtsCommuteQueueGenerationMode?>(null) }
    LaunchedEffect(commuteBuildGenerationMode) {
        if (commuteBuildGenerationMode != null) {
            pendingGenerationMode = commuteBuildGenerationMode
        } else if (pendingGenerationMode != null) {
            showGenerateDialog = false
            pendingGenerationMode = null
        }
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        QueueHeader(
            state = state,
            onSwitchMode = onSwitchMode,
            onGenerateCommuteBrief = {
                showGenerateDialog = true
            },
            onClear = onClear,
            onSetSleepTimer = onSetSleepTimer,
            modifier = Modifier.padding(horizontal = QUEUE_SHEET_HORIZONTAL_PADDING_DP.dp),
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
            onToggleCurrentStarred = onToggleCurrentStarred,
            modifier = Modifier.padding(horizontal = QUEUE_SHEET_HORIZONTAL_PADDING_DP.dp),
        )

        if (state.items.isEmpty()) {
            return@Column
        }

        LazyColumn {
            items(state.items, key = { "${it.contentType}-${it.articleId}" }) { item ->
                val isCurrent = item.articleId == state.currentArticleId
                val highlightColor =
                    if (isCurrent) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                        Color.Transparent
                    }
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(highlightColor)
                            .padding(
                                horizontal = QUEUE_SHEET_HORIZONTAL_PADDING_DP.dp,
                                vertical = 4.dp,
                            ),
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
                            text = item.queueMetadataText(),
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                if (isCurrent) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
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

    GenerateCommuteBriefDialog(
        visible = showGenerateDialog,
        state = state,
        generationMode = commuteBuildGenerationMode ?: pendingGenerationMode,
        onGenerate = { generationMode ->
            pendingGenerationMode = generationMode
            onGenerateCommuteBrief(generationMode)
        },
        onDismiss = { showGenerateDialog = false },
    )
}

@Composable
private fun QueueHeader(
    state: TtsQueueState,
    onSwitchMode: (TtsQueueMode) -> Unit,
    onGenerateCommuteBrief: () -> Unit,
    onClear: () -> Unit,
    onSetSleepTimer: (TtsSleepTimerOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var modeMenuExpanded by remember { mutableStateOf(false) }
    var pendingModeSwitch by remember { mutableStateOf<TtsQueueMode?>(null) }
    LaunchedEffect(modeMenuExpanded, pendingModeSwitch) {
        val mode = pendingModeSwitch ?: return@LaunchedEffect
        if (!modeMenuExpanded) {
            delay(MODE_SWITCH_AFTER_MENU_CLOSE_DELAY_MS)
            pendingModeSwitch = null
            onSwitchMode(mode)
        }
    }
    Row(
        modifier = modifier.fillMaxWidth(),
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
                        pendingModeSwitch = TtsQueueMode.Normal
                        modeMenuExpanded = false
                    },
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(id = R.string.commute_mode)) },
                    onClick = {
                        pendingModeSwitch = TtsQueueMode.Commute
                        modeMenuExpanded = false
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
private fun GenerateCommuteBriefDialog(
    state: TtsQueueState,
    visible: Boolean,
    generationMode: TtsCommuteQueueGenerationMode?,
    onGenerate: (TtsCommuteQueueGenerationMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val stats = remember(state.items, state.commuteMeta) { state.collectCommuteBriefStats() }
    val isGenerating = generationMode != null
    RYDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.generate_commute_brief_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (stats != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(id = R.string.commute_brief_current_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        CommuteBriefStatRow(
                            label = stringResource(id = R.string.commute_brief_generated_at_label),
                            value = formatCommuteGeneratedDateTime(stats.generatedAtMillis),
                        )
                        CommuteBriefStatRow(
                            label = stringResource(id = R.string.commute_brief_content_size_label),
                            value = stringResource(
                                id = R.string.commute_brief_content_size_value,
                                stats.itemCount,
                                formatCount(stats.totalChars),
                            ),
                        )
                        CommuteBriefStatRow(
                            label = stringResource(id = R.string.commute_brief_duration_label),
                            value =
                                stats.targetDurationMinutes?.let {
                                    stringResource(
                                        id = R.string.commute_brief_duration_stats_with_target,
                                        stats.estimatedDurationMinutes,
                                        it,
                                    )
                                } ?: stringResource(
                                    id = R.string.commute_brief_duration_stats,
                                    stats.estimatedDurationMinutes,
                                ),
                        )
                    }
                    HorizontalDivider()
                }
                Text(
                    text = stringResource(id = R.string.commute_brief_generate_new_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (generationMode != null) {
                    GenerateCommuteBriefLoading(generationMode = generationMode)
                } else {
                    GenerateCommuteBriefOption(
                        title = stringResource(id = R.string.commute_brief_generate_newest_first),
                        desc = stringResource(id = R.string.commute_brief_generate_newest_first_desc),
                        onClick = { onGenerate(TtsCommuteQueueGenerationMode.NewestFirst) },
                    )
                    GenerateCommuteBriefOption(
                        title = stringResource(id = R.string.commute_brief_generate_ai_recommended),
                        desc = stringResource(id = R.string.commute_brief_generate_ai_recommended_desc),
                        badge = stringResource(id = R.string.commute_brief_ai_recommended_badge),
                        onClick = { onGenerate(TtsCommuteQueueGenerationMode.AiRecommended) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = if (isGenerating) R.string.close else R.string.cancel))
            }
        },
    )
}

@Composable
private fun CommuteBriefStatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun GenerateCommuteBriefOption(
    title: String,
    desc: String,
    onClick: () -> Unit,
    badge: String? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    if (badge != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier =
                                Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GenerateCommuteBriefLoading(generationMode: TtsCommuteQueueGenerationMode) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text =
                        stringResource(
                            id =
                                when (generationMode) {
                                    TtsCommuteQueueGenerationMode.NewestFirst ->
                                        R.string.commute_brief_generating_newest_first
                                    TtsCommuteQueueGenerationMode.AiRecommended ->
                                        R.string.commute_brief_generating_ai_recommended
                                },
                        ),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text =
                        stringResource(
                            id =
                                when (generationMode) {
                                    TtsCommuteQueueGenerationMode.NewestFirst ->
                                        R.string.commute_brief_generating_newest_first_desc
                                    TtsCommuteQueueGenerationMode.AiRecommended ->
                                        R.string.commute_brief_generating_ai_recommended_desc
                                },
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
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

@Composable
private fun TtsQueueItem.queueMetadataText(): String {
    val source =
        if (contentType == TtsQueueContentType.AiSummary) {
            stringResource(id = R.string.commute_brief_summary_item, feedName)
        } else {
            feedName
        }
    val date = publishedAtMillis?.let(::formatQueueItemDate)
    return if (date == null) {
        source
    } else {
        stringResource(id = R.string.tts_queue_item_source_date, source, date)
    }
}

private fun formatCommuteGeneratedDateTime(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun formatQueueItemDate(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))

private fun formatCount(count: Int): String = NumberFormat.getIntegerInstance(Locale.getDefault()).format(count)
