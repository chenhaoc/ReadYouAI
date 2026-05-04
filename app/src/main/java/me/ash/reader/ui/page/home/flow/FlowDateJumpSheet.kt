package me.ash.reader.ui.page.home.flow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun FlowDateJumpSheet(
    items: List<ArticleDateJumpItem>,
    onSelect: (ArticleDateJumpItem) -> Unit,
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
            ListItem(
                modifier =
                    Modifier.fillMaxWidth()
                        .clickable { onSelect(item) }
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
            if (index != items.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )
            }
        }
    }
}

private fun Date.toDateJumpLabel(context: android.content.Context): String {
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
