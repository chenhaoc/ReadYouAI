package me.ash.reader.ui.page.home.reading

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.ash.reader.R

@Composable
fun AiSummaryCard(
    summary: String,
    isLoading: Boolean,
    error: String?,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit = {},
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        imageVector = Icons.Outlined.Psychology,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Text(
                        text = stringResource(id = R.string.ai_summary),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (isLoading) {
                        Spacer(modifier = Modifier.size(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                IconButton(onClick = onToggleExpanded, enabled = !isLoading) {
                    Icon(
                        imageVector =
                            if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription =
                            stringResource(
                                id =
                                    if (isExpanded) R.string.expand_less else R.string.expand_more,
                            ),
                    )
                }
            }

            if (isExpanded) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading) {
                    Column {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(id = R.string.ai_summary_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (summary.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                if (error != null) {
                    Text(
                        text = stringResource(id = R.string.ai_summary_error, error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    if (summary.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                if (summary.isNotEmpty()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
