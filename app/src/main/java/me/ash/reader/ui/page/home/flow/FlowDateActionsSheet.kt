package me.ash.reader.ui.page.home.flow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.ash.reader.R
import me.ash.reader.domain.model.article.ArticleDateJumpItem

@Composable
fun FlowDateActionsSheet(
    item: ArticleDateJumpItem,
    onAddToPlaylist: () -> Unit,
    onAppendToSummaryList: () -> Unit,
    onReplaceSummaryList: () -> Unit,
    onMarkAsRead: () -> Unit,
    onMarkAsUnread: () -> Unit,
) {
    val context = LocalContext.current
    val dateText = remember(item.date) { item.date.toDateJumpLabel(context) }

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item {
            Text(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
                text = stringResource(R.string.date_actions),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 2.dp),
                text = dateText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
                text =
                    pluralStringResource(
                        R.plurals.jump_to_date_articles,
                        item.articleCount,
                        item.articleCount,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        item {
            DateActionItem(
                text = stringResource(R.string.add_to_playlist),
                onClick = onAddToPlaylist,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
            DateActionItem(
                text = stringResource(R.string.append_to_summary_list),
                onClick = onAppendToSummaryList,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
            DateActionItem(
                text = stringResource(R.string.replace_summary_list),
                onClick = onReplaceSummaryList,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
            DateActionItem(
                text = stringResource(R.string.mark_date_articles_as_read),
                onClick = onMarkAsRead,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
            DateActionItem(
                text = stringResource(R.string.mark_date_articles_as_unread),
                onClick = onMarkAsUnread,
            )
        }
    }
}

@Composable
private fun DateActionItem(
    text: String,
    onClick: () -> Unit,
) {
    ListItem(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp),
        headlineContent = { Text(text = text) },
    )
}
