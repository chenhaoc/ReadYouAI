package me.ash.reader.ui.page.home.reading

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import me.ash.reader.R
import me.ash.reader.infrastructure.preference.LocalReadingTextFontSize

@Composable
fun AiSummaryCard(
    summary: String,
    isLoading: Boolean,
    error: String?,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit = {},
    onVisibilityChanged: (Boolean) -> Unit = {},
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val readingTextFontSize = LocalReadingTextFontSize.current
    val minVisibleHeight = with(density) { 24.dp.toPx() }
    var lastVisibility by remember { mutableStateOf<Boolean?>(null) }

    Surface(
        modifier =
            Modifier.fillMaxWidth()
                .padding(top = 20.dp)
                .onGloballyPositioned { coordinates ->
                    val bounds: Rect = coordinates.boundsInWindow()
                    val viewportWidth = view.width.toFloat()
                    val viewportHeight = view.height.toFloat()
                    val visibleWidth =
                        max(0f, min(bounds.right, viewportWidth) - max(bounds.left, 0f))
                    val visibleHeight =
                        max(0f, min(bounds.bottom, viewportHeight) - max(bounds.top, 0f))
                    val isVisible =
                        visibleWidth > 0f &&
                            visibleHeight >= min(minVisibleHeight, bounds.height)
                    if (lastVisibility != isVisible) {
                        lastVisibility = isVisible
                        onVisibilityChanged(isVisible)
                    }
                },
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onToggleExpanded),
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
                Icon(
                    imageVector =
                        if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription =
                        stringResource(
                            id = if (isExpanded) R.string.expand_less else R.string.expand_more,
                        ),
                )
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
                        style =
                            MaterialTheme.typography.bodyLarge.copy(
                                fontSize = readingTextFontSize.sp,
                            ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
