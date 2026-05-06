package me.ash.reader.ui.page.home.flow

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import me.ash.reader.R
import me.ash.reader.domain.model.article.ArticleDateJumpItem
import me.ash.reader.ui.component.menu.AnimatedDropdownMenu
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FlowDateJumpSheet(
    items: List<ArticleDateJumpItem>,
    onSelect: (ArticleDateJumpItem) -> Unit,
    onAddToPlaylist: ((ArticleDateJumpItem) -> Unit)? = null,
    onAppendToSummaryList: ((ArticleDateJumpItem) -> Unit)? = null,
    onReplaceSummaryList: ((ArticleDateJumpItem) -> Unit)? = null,
    onMarkAsRead: ((ArticleDateJumpItem) -> Unit)? = null,
    onMarkAsUnread: ((ArticleDateJumpItem) -> Unit)? = null,
) {
    val context = LocalContext.current
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item {
            Text(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
                text = stringResource(R.string.jump_to_date),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
                text = stringResource(R.string.jump_to_date_summary, items.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        itemsIndexed(items, key = { _, item -> "${item.date.time}-${item.articleOffset}" }) { index, item ->
            val dateText = remember(item.date) { item.date.toDateJumpLabel(context) }
            DateJumpItem(
                item = item,
                dateText = dateText,
                onSelect = onSelect,
                onAddToPlaylist = onAddToPlaylist,
                onAppendToSummaryList = onAppendToSummaryList,
                onReplaceSummaryList = onReplaceSummaryList,
                onMarkAsRead = onMarkAsRead,
                onMarkAsUnread = onMarkAsUnread,
            )
            if (index != items.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DateJumpItem(
    item: ArticleDateJumpItem,
    dateText: String,
    onSelect: (ArticleDateJumpItem) -> Unit,
    onAddToPlaylist: ((ArticleDateJumpItem) -> Unit)?,
    onAppendToSummaryList: ((ArticleDateJumpItem) -> Unit)?,
    onReplaceSummaryList: ((ArticleDateJumpItem) -> Unit)?,
    onMarkAsRead: ((ArticleDateJumpItem) -> Unit)?,
    onMarkAsUnread: ((ArticleDateJumpItem) -> Unit)?,
) {
    val hasLongPressMenu =
        onAddToPlaylist != null ||
            onAppendToSummaryList != null ||
            onReplaceSummaryList != null ||
            onMarkAsRead != null ||
            onMarkAsUnread != null

    var isMenuExpanded by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(IntOffset.Zero) }

    Box(
        modifier =
            Modifier.fillMaxWidth()
                .pointerInput(hasLongPressMenu) {
                    if (!hasLongPressMenu) return@pointerInput
                    awaitEachGesture {
                        while (true) {
                            awaitFirstDown(requireUnconsumed = false).let {
                                menuOffset = it.position.round()
                            }
                        }
                    }
                }
                .wrapContentSize()
    ) {
        ListItem(
            modifier =
                Modifier.fillMaxWidth()
                    .combinedClickable(
                        onClick = { onSelect(item) },
                        onLongClick = if (hasLongPressMenu) ({ isMenuExpanded = true }) else null,
                        onLongClickLabel = stringResource(R.string.options),
                    )
                    .padding(horizontal = 8.dp),
            headlineContent = { Text(text = dateText) },
            supportingContent = {
                Text(
                    text =
                        pluralStringResource(
                            R.plurals.jump_to_date_articles,
                            item.articleCount,
                            item.articleCount,
                        )
                )
            },
        )

        if (hasLongPressMenu) {
            AnimatedDropdownMenu(
                modifier = Modifier.padding(horizontal = 12.dp),
                expanded = isMenuExpanded,
                onDismissRequest = { isMenuExpanded = false },
                offset = menuOffset,
            ) {
                FlowDateJumpItemMenuContent(
                    item = item,
                    onAddToPlaylist = onAddToPlaylist,
                    onAppendToSummaryList = onAppendToSummaryList,
                    onReplaceSummaryList = onReplaceSummaryList,
                    onMarkAsRead = onMarkAsRead,
                    onMarkAsUnread = onMarkAsUnread,
                ) {
                    isMenuExpanded = false
                }
            }
        }
    }
}

@Composable
private fun FlowDateJumpItemMenuContent(
    item: ArticleDateJumpItem,
    iconSize: DpSize = DpSize(width = 20.dp, height = 20.dp),
    onAddToPlaylist: ((ArticleDateJumpItem) -> Unit)?,
    onAppendToSummaryList: ((ArticleDateJumpItem) -> Unit)?,
    onReplaceSummaryList: ((ArticleDateJumpItem) -> Unit)?,
    onMarkAsRead: ((ArticleDateJumpItem) -> Unit)?,
    onMarkAsUnread: ((ArticleDateJumpItem) -> Unit)?,
    onItemClick: (() -> Unit)? = null,
) {
    onMarkAsRead?.let {
        DropdownMenuItem(
            text = { Text(text = stringResource(id = R.string.mark_date_articles_as_read)) },
            onClick = {
                onMarkAsRead(item)
                onItemClick?.invoke()
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.FiberManualRecord,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                )
            },
        )
    }
    onMarkAsUnread?.let {
        DropdownMenuItem(
            text = { Text(text = stringResource(id = R.string.mark_date_articles_as_unread)) },
            onClick = {
                onMarkAsUnread(item)
                onItemClick?.invoke()
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.FiberManualRecord,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                )
            },
        )
    }
    if ((onMarkAsRead != null || onMarkAsUnread != null) &&
        (onAddToPlaylist != null || onAppendToSummaryList != null || onReplaceSummaryList != null)
    ) {
        HorizontalDivider()
    }
    onAddToPlaylist?.let {
        DropdownMenuItem(
            text = { Text(text = stringResource(id = R.string.add_to_playlist)) },
            onClick = {
                onAddToPlaylist(item)
                onItemClick?.invoke()
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Headphones,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                )
            },
        )
    }
    onAppendToSummaryList?.let {
        DropdownMenuItem(
            text = { Text(text = stringResource(id = R.string.append_to_summary_list)) },
            onClick = {
                onAppendToSummaryList(item)
                onItemClick?.invoke()
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                )
            },
        )
    }
    onReplaceSummaryList?.let {
        DropdownMenuItem(
            text = { Text(text = stringResource(id = R.string.replace_summary_list)) },
            onClick = {
                onReplaceSummaryList(item)
                onItemClick?.invoke()
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                )
            },
        )
    }
}

internal fun Date.toDateJumpLabel(context: Context): String {
    val locale = Locale.getDefault()
    val dateFormatter = DateFormat.getDateInstance(DateFormat.FULL, locale)
    val fullDate = dateFormatter.format(this)
    val today = dateFormatter.format(Date())
    val yesterday =
        dateFormatter.format(
            Calendar.getInstance().apply {
                time = Date()
                add(Calendar.DAY_OF_MONTH, -1)
            }.time
        )
    return when (fullDate) {
        today -> context.getString(R.string.jump_to_date_special_day, fullDate, context.getString(R.string.today))
        yesterday ->
            context.getString(
                R.string.jump_to_date_special_day,
                fullDate,
                context.getString(R.string.yesterday),
            )
        else -> fullDate
    }
}
