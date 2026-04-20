package me.ash.reader.ui.page.home.reading

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.ash.reader.R
import me.ash.reader.domain.model.ai.AiChatMessage

@Composable
fun AiChatSheet(
    messages: List<AiChatMessage>,
    includeFullContent: Boolean,
    selectedSnippet: String?,
    isSending: Boolean,
    error: String?,
    onIncludeFullContentChange: (Boolean) -> Unit,
    onQuickAction: (AiChatQuickAction) -> Unit,
    onSendMessage: (String) -> Unit,
    onClearHistory: () -> Unit,
    onClearSelectedSnippet: () -> Unit,
    onClose: () -> Unit,
) {
    val draftState = rememberTextFieldState()
    val hasSelection = !selectedSnippet.isNullOrBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxSize(0.7f)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.ai_chat),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    enabled = messages.isNotEmpty() && !isSending,
                    onClick = onClearHistory,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = stringResource(R.string.ai_chat_new_conversation),
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.close),
                    )
                }
            }
        }

        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                FilterChip(
                    modifier = Modifier.height(28.dp),
                    selected = includeFullContent,
                    onClick = { onIncludeFullContentChange(!includeFullContent) },
                    label = {
                        Text(
                            text = stringResource(
                                if (includeFullContent) {
                                    R.string.ai_chat_full_context_on
                                } else {
                                    R.string.ai_chat_full_context_off
                                }
                            )
                        )
                    },
                )
                Text(
                    text =
                        if (hasSelection) {
                            stringResource(R.string.ai_chat_selected_chars, selectedSnippet!!.length)
                        } else {
                            stringResource(R.string.ai_chat_no_selection)
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(7.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                QuickActionChip(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.ai_chat_quick_explain_article),
                    onClick = { onQuickAction(AiChatQuickAction.ExplainArticle) },
                )
                QuickActionChip(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.ai_chat_quick_explain_selection),
                    enabled = hasSelection,
                    onClick = { onQuickAction(AiChatQuickAction.ExplainSelection) },
                )
            }
            Spacer(modifier = Modifier.height(7.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                QuickActionChip(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.ai_chat_quick_background),
                    onClick = { onQuickAction(AiChatQuickAction.GiveBackground) },
                )
                QuickActionChip(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.ai_chat_quick_introduce),
                    onClick = { onQuickAction(AiChatQuickAction.Introduce) },
                )
            }
        }

        if (hasSelection) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, end = 6.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = selectedSnippet!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(
                        modifier = Modifier.size(20.dp),
                        onClick = onClearSelectedSnippet,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Clear,
                            contentDescription = stringResource(R.string.clear),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (!error.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.ai_chat_error, error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (messages.isEmpty() && !isSending) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.ai_chat_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                AiChatConversationContent(
                    modifier = Modifier.fillMaxSize(),
                    messages = messages,
                    isSending = isSending,
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                state = draftState,
                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                placeholder = {
                    Text(text = stringResource(R.string.ai_chat_input_hint))
                },
                contentPadding = TextFieldDefaults.contentPaddingWithoutLabel(
                    top = 8.dp,
                    bottom = 8.dp,
                ),
                lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.MultiLine(maxHeightInLines = 4),
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                enabled = draftState.text.isNotBlank() && !isSending,
                onClick = {
                    val question = draftState.text.toString().trim()
                    draftState.clearText()
                    onSendMessage(question)
                },
            ) {
                Icon(
                    imageVector = Icons.Outlined.Send,
                    contentDescription = stringResource(R.string.send),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun QuickActionChip(
    modifier: Modifier = Modifier,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    AssistChip(
        modifier = modifier.height(28.dp),
        enabled = enabled,
        onClick = onClick,
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        colors = AssistChipDefaults.assistChipColors(),
    )
}
