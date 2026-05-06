package me.ash.reader.ui.page.home.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import me.ash.reader.R
import me.ash.reader.domain.model.article.ArticleDateJumpItem
import me.ash.reader.ui.page.adaptive.toLocalDayRange

@Composable
fun FlowMarkReadByDateSheet(
    items: List<ArticleDateJumpItem>,
    selectedDates: Set<Long>,
    currentDateKey: Long?,
    onToggleDate: (ArticleDateJumpItem) -> Unit,
    onSelectCurrent: () -> Unit,
    onSelectNewer: () -> Unit,
    onSelectOlder: () -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onConfirm: () -> Unit,
) {
    val context = LocalContext.current
    val selectedCount = selectedDates.size
    val selectedArticleCount = remember(items, selectedDates) { items.filter { it.dayKey in selectedDates }.sumOf { it.articleCount } }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.mark_read_by_date_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.mark_read_by_date_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = onClear,
                enabled = selectedCount > 0,
            ) {
                Text(text = stringResource(R.string.clear))
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 24.dp),
            color = MaterialTheme.colorScheme.primary,
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ShortcutChip(
                    text = stringResource(R.string.mark_read_pick_current),
                    onClick = onSelectCurrent,
                )
                ShortcutChip(
                    text = stringResource(R.string.mark_read_pick_newer),
                    onClick = onSelectNewer,
                )
                ShortcutChip(
                    text = stringResource(R.string.mark_read_pick_older),
                    onClick = onSelectOlder,
                )
            }
            TextButton(
                onClick = onSelectAll,
                enabled = items.isNotEmpty() && selectedCount < items.size,
            ) {
                Text(text = stringResource(R.string.select_all))
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
        ) {
            items(items, key = { "${it.dayKey}-${it.articleOffset}" }) { item ->
                val checked = item.dayKey in selectedDates
                val isCurrent = currentDateKey == item.dayKey
                val dateText = remember(item.date) { item.date.toDateJumpLabel(context) }
                DateSelectionItem(
                    dateText = dateText,
                    articleCount = item.articleCount,
                    checked = checked,
                    isCurrent = isCurrent,
                    onClick = { onToggleDate(item) },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text =
                        pluralStringResource(
                            R.plurals.mark_read_selected_dates,
                            selectedCount,
                            selectedCount,
                            selectedArticleCount,
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            FilledTonalButton(
                onClick = onConfirm,
                enabled = selectedCount > 0,
            ) {
                Text(text = stringResource(R.string.mark_as_read))
            }
        }
    }
}

@Composable
private fun DateSelectionItem(
    dateText: String,
    articleCount: Int,
    checked: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val highlightColor =
        if (isCurrent) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            Color.Transparent
        }

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .background(highlightColor)
                .clickable(onClick = onClick)
                .padding(start = 24.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text =
                    buildAnnotatedString {
                        append(dateText)
                        if (isCurrent) {
                            append(" ")
                            withStyle(
                                MaterialTheme.typography.labelMedium.toSpanStyle().copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                )
                            ) {
                                append(stringResource(R.string.mark_read_current_position))
                            }
                        }
                    },
                style = MaterialTheme.typography.titleMedium,
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
                    pluralStringResource(
                        R.plurals.jump_to_date_articles,
                        articleCount,
                        articleCount,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = { onClick() },
            )
        }
    }
}

@Composable
private fun ShortcutChip(
    text: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Text(text = text)
    }
}

internal val ArticleDateJumpItem.dayKey: Long
    get() = date.toLocalDayRange().first.time
