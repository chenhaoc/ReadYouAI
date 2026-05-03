package me.ash.reader.ui.page.settings.ai

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import me.ash.reader.R
import me.ash.reader.infrastructure.preference.LocalAiApiKey
import me.ash.reader.infrastructure.preference.LocalAiBaseUrl
import me.ash.reader.domain.service.PendingAiSummaryEnqueuer
import me.ash.reader.infrastructure.preference.LocalSettings
import me.ash.reader.infrastructure.preference.AiBackgroundSummaryLimitPreference
import me.ash.reader.infrastructure.preference.LocalAiBackgroundSummary
import me.ash.reader.infrastructure.preference.LocalAiBackgroundSummaryBackfillOnSync
import me.ash.reader.infrastructure.preference.LocalAiBackgroundSummaryLimit
import me.ash.reader.infrastructure.preference.LocalAiModel
import me.ash.reader.infrastructure.preference.LocalAiChatPrompt
import me.ash.reader.infrastructure.preference.LocalAiCommuteBriefRecommendationPrompt
import me.ash.reader.infrastructure.preference.LocalAiSummarizationPrompt
import me.ash.reader.infrastructure.preference.LocalAiTranslationPrompt
import me.ash.reader.infrastructure.preference.summary
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYDialog
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.component.base.RYSwitch
import me.ash.reader.ui.component.base.RadioDialog
import me.ash.reader.ui.component.base.RadioDialogOption
import me.ash.reader.ui.component.base.Subtitle
import me.ash.reader.ui.component.base.TextFieldDialog
import me.ash.reader.ui.page.home.reading.resolveAiChatPrompt
import me.ash.reader.ui.page.home.reading.resolveAiCommuteBriefRecommendationPrompt
import me.ash.reader.ui.page.home.reading.resolveAiSummarizationPrompt
import me.ash.reader.ui.page.home.reading.resolveAiTranslationPrompt
import me.ash.reader.ui.ext.showToast
import me.ash.reader.ui.page.settings.SettingItem
import me.ash.reader.ui.theme.palette.onLight

@Composable
fun AiSettingsPage(
    aiSettingsViewModel: AiSettingsViewModel = hiltViewModel(),
    onBack: () -> Unit,
    navigateToPresetManager: () -> Unit,
) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val aiBaseUrl = LocalAiBaseUrl.current
    val aiApiKey = LocalAiApiKey.current
    val aiModel = LocalAiModel.current
    val aiSummarizationPrompt = LocalAiSummarizationPrompt.current
    val aiCommuteBriefRecommendationPrompt = LocalAiCommuteBriefRecommendationPrompt.current
    val aiTranslationPrompt = LocalAiTranslationPrompt.current
    val aiChatPrompt = LocalAiChatPrompt.current
    val aiBackgroundSummary = LocalAiBackgroundSummary.current
    val aiBackgroundSummaryLimit = LocalAiBackgroundSummaryLimit.current
    val aiBackgroundSummaryBackfillOnSync = LocalAiBackgroundSummaryBackfillOnSync.current
    
    val scope = rememberCoroutineScope()
    
    var presetDialogVisible by remember { mutableStateOf(false) }
    var promptDialogVisible by remember { mutableStateOf(false) }
    var commuteBriefRecommendationPromptDialogVisible by remember { mutableStateOf(false) }
    var translationPromptDialogVisible by remember { mutableStateOf(false) }
    var chatPromptDialogVisible by remember { mutableStateOf(false) }
    var backgroundSummaryLimitDialogVisible by remember { mutableStateOf(false) }
    var backfillConfirmDialogVisible by remember { mutableStateOf(false) }

    val summarizationPromptState = rememberTextFieldState()
    val commuteBriefRecommendationPromptState = rememberTextFieldState()
    val translationPromptState = rememberTextFieldState()
    val chatPromptState = rememberTextFieldState()
    
    val availableModels = remember { mutableStateListOf<String>() }
    var isLoadingModels by remember { mutableStateOf(false) }
    var fetchError by remember { mutableStateOf<String?>(null) }
    var testConnectionState by remember { mutableStateOf(AiConnectionTestState.Idle) }
    var testWebSearchState by remember { mutableStateOf(AiConnectionTestState.Idle) }
    
    LaunchedEffect(aiBaseUrl.value, aiApiKey.value, aiModel.value) {
        testConnectionState = AiConnectionTestState.Idle
        testWebSearchState = AiConnectionTestState.Idle
        if (aiBaseUrl.value.isNotEmpty() && aiApiKey.value.isNotEmpty()) {
            isLoadingModels = true
            fetchError = null
            availableModels.clear()
            
            aiSettingsViewModel.fetchModels(
                baseUrl = aiBaseUrl.value,
                apiKey = aiApiKey.value,
                onSuccess = { models ->
                    availableModels.addAll(models)
                    isLoadingModels = false
                },
                onError = { error ->
                    fetchError = error
                    isLoadingModels = false
                }
            )
        }
    }

    LaunchedEffect(promptDialogVisible, aiSummarizationPrompt.value) {
        if (promptDialogVisible) {
            summarizationPromptState.setTextAndPlaceCursorAtEnd(
                resolveAiSummarizationPrompt(aiSummarizationPrompt.value)
            )
        }
    }

    LaunchedEffect(
        commuteBriefRecommendationPromptDialogVisible,
        aiCommuteBriefRecommendationPrompt.value,
    ) {
        if (commuteBriefRecommendationPromptDialogVisible) {
            commuteBriefRecommendationPromptState.setTextAndPlaceCursorAtEnd(
                resolveAiCommuteBriefRecommendationPrompt(
                    aiCommuteBriefRecommendationPrompt.value,
                )
            )
        }
    }

    LaunchedEffect(translationPromptDialogVisible, aiTranslationPrompt.value) {
        if (translationPromptDialogVisible) {
            translationPromptState.setTextAndPlaceCursorAtEnd(
                resolveAiTranslationPrompt(aiTranslationPrompt.value)
            )
        }
    }

    LaunchedEffect(chatPromptDialogVisible, aiChatPrompt.value) {
        if (chatPromptDialogVisible) {
            chatPromptState.setTextAndPlaceCursorAtEnd(
                resolveAiChatPrompt(aiChatPrompt.value)
            )
        }
    }

    RYScaffold(
        containerColor = MaterialTheme.colorScheme.surface onLight MaterialTheme.colorScheme.inverseOnSurface,
        navigationIcon = {
            FeedbackIconButton(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onBack
            )
        },
        content = {
            LazyColumn {
                item {
                    DisplayText(
                        text = stringResource(R.string.ai_settings),
                        desc = stringResource(R.string.ai_settings_desc)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.api_configuration)
                    )
                    SettingItem(
                        title = stringResource(R.string.ai_manage_configurations),
                        desc = stringResource(R.string.ai_manage_configurations_desc),
                        onClick = navigateToPresetManager,
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.ai_default_configuration),
                        desc = settings.aiConfigPresets.firstOrNull { it.id == settings.aiCurrentPresetId }?.name
                            ?: stringResource(R.string.ai_configuration_empty),
                        onClick = { presetDialogVisible = true },
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.ai_default_configuration_info),
                        desc = buildString {
                            append(aiModel.toDesc(context))
                            append(" · ")
                            append(aiBaseUrl.toDesc(context))
                        },
                        onClick = {},
                    ) {}
                }

                if (isLoadingModels) {
                    item {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.ai_fetch_models),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                if (fetchError != null) {
                    item {
                        Text(
                            text = fetchError!!,
                            modifier = Modifier.padding(horizontal = 24.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                item {
                    SettingItem(
                        enabled = testConnectionState != AiConnectionTestState.Testing,
                        title = stringResource(R.string.ai_test_connection),
                        desc =
                            stringResource(
                                when (testConnectionState) {
                                    AiConnectionTestState.Idle -> R.string.ai_test_connection_desc
                                    AiConnectionTestState.Testing -> R.string.ai_test_connection_testing
                                    AiConnectionTestState.Success -> R.string.ai_test_connection_success
                                    AiConnectionTestState.Failed -> R.string.ai_test_connection_retry
                                }
                            ),
                        onClick = {
                            val baseUrl = aiBaseUrl.value.trim()
                            val apiKey = aiApiKey.value.trim()
                            val model = aiModel.value.trim()
                            if (baseUrl.isBlank() || apiKey.isBlank() || model.isBlank()) {
                                context.showToast(context.getString(R.string.ai_test_connection_missing_config))
                                return@SettingItem
                            }
                            testConnectionState = AiConnectionTestState.Testing
                            aiSettingsViewModel.testConnection(
                                baseUrl = baseUrl,
                                apiKey = apiKey,
                                model = model,
                                onSuccess = {
                                    testConnectionState = AiConnectionTestState.Success
                                    context.showToast(context.getString(R.string.ai_test_connection_success))
                                },
                                onError = { error ->
                                    testConnectionState = AiConnectionTestState.Failed
                                    context.showToast(
                                        context.getString(R.string.ai_test_connection_failed, error)
                                    )
                                },
                            )
                        },
                    ) {
                        AiConnectionTestStateIcon(state = testConnectionState)
                    }
                    SettingItem(
                        enabled = testWebSearchState != AiConnectionTestState.Testing,
                        title = stringResource(R.string.ai_test_web_search),
                        desc =
                            stringResource(
                                when (testWebSearchState) {
                                    AiConnectionTestState.Idle -> R.string.ai_test_web_search_desc
                                    AiConnectionTestState.Testing -> R.string.ai_test_web_search_testing
                                    AiConnectionTestState.Success -> R.string.ai_test_web_search_success
                                    AiConnectionTestState.Failed -> R.string.ai_test_web_search_retry
                                }
                            ),
                        onClick = {
                            val baseUrl = aiBaseUrl.value.trim()
                            val apiKey = aiApiKey.value.trim()
                            val model = aiModel.value.trim()
                            if (baseUrl.isBlank() || apiKey.isBlank() || model.isBlank()) {
                                context.showToast(context.getString(R.string.ai_test_connection_missing_config))
                                return@SettingItem
                            }
                            testWebSearchState = AiConnectionTestState.Testing
                            aiSettingsViewModel.testWebSearch(
                                baseUrl = baseUrl,
                                apiKey = apiKey,
                                model = model,
                                onSuccess = {
                                    testWebSearchState = AiConnectionTestState.Success
                                    context.showToast(context.getString(R.string.ai_test_web_search_success))
                                },
                                onError = { error ->
                                    testWebSearchState = AiConnectionTestState.Failed
                                    context.showToast(
                                        context.getString(R.string.ai_test_web_search_failed, error)
                                    )
                                },
                            )
                        },
                    ) {
                        AiConnectionTestStateIcon(state = testWebSearchState)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.summarization_settings)
                    )
                    SettingItem(
                        title = stringResource(R.string.ai_summarization_prompt),
                        desc = aiSummarizationPrompt.toDesc(context),
                        onClick = {
                            promptDialogVisible = true
                        }
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.ai_commute_brief_recommendation_prompt),
                        desc = aiCommuteBriefRecommendationPrompt.toDesc(context),
                        onClick = {
                            commuteBriefRecommendationPromptDialogVisible = true
                        }
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.ai_background_summary),
                        desc = stringResource(R.string.ai_background_summary_desc),
                        onClick = {
                            aiBackgroundSummary.toggle(context, scope)
                        }
                    ) {
                        RYSwitch(activated = aiBackgroundSummary.value) {
                            aiBackgroundSummary.toggle(context, scope)
                        }
                    }
                    SettingItem(
                        title = stringResource(R.string.ai_background_summary_limit),
                        desc = aiBackgroundSummaryLimit.toDesc(context),
                        onClick = {
                            backgroundSummaryLimitDialogVisible = true
                        }
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.ai_background_summary_backfill_on_sync),
                        desc = stringResource(R.string.ai_background_summary_backfill_on_sync_desc),
                        onClick = {
                            aiBackgroundSummaryBackfillOnSync.toggle(context, scope)
                        }
                    ) {
                        RYSwitch(activated = aiBackgroundSummaryBackfillOnSync.value) {
                            aiBackgroundSummaryBackfillOnSync.toggle(context, scope)
                        }
                    }
                    SettingItem(
                        title = stringResource(R.string.ai_background_summary_backfill_unread),
                        desc = stringResource(R.string.ai_background_summary_backfill_unread_desc),
                        onClick = {
                            backfillConfirmDialogVisible = true
                        }
                    ) {}

                    Spacer(modifier = Modifier.height(24.dp))
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.translation_settings)
                    )
                    SettingItem(
                        title = stringResource(R.string.ai_translation_prompt),
                        desc = aiTranslationPrompt.toDesc(context),
                        onClick = {
                            translationPromptDialogVisible = true
                        }
                    ) {}

                    Spacer(modifier = Modifier.height(24.dp))
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.chat_settings)
                    )
                    SettingItem(
                        title = stringResource(R.string.ai_chat_prompt),
                        desc = aiChatPrompt.toDesc(context),
                        onClick = {
                            chatPromptDialogVisible = true
                        }
                    ) {}
                    Spacer(modifier = Modifier.height(24.dp))
                }
                
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    )

    RadioDialog(
        visible = presetDialogVisible,
        title = stringResource(R.string.ai_default_configuration),
        options = settings.aiConfigPresets.map { preset ->
            RadioDialogOption(
                text = preset.name,
                selected = preset.id == settings.aiCurrentPresetId,
            ) {
                aiSettingsViewModel.setCurrentPreset(context, preset.id)
            }
        },
        onDismissRequest = {
            presetDialogVisible = false
        },
    )

    RadioDialog(
        visible = backgroundSummaryLimitDialogVisible,
        title = stringResource(R.string.ai_background_summary_limit),
        options = AiBackgroundSummaryLimitPreference.values.map { option ->
            RadioDialogOption(
                text = option.toDesc(context),
                selected = option == aiBackgroundSummaryLimit,
            ) {
                option.put(context, scope)
                backgroundSummaryLimitDialogVisible = false
            }
        },
        onDismissRequest = {
            backgroundSummaryLimitDialogVisible = false
        }
    )

    RYDialog(
        visible = backfillConfirmDialogVisible,
        onDismissRequest = { backfillConfirmDialogVisible = false },
        title = { Text(text = stringResource(R.string.ai_background_summary_backfill_confirm_title)) },
        text = {
            Text(
                text = stringResource(
                    R.string.ai_background_summary_backfill_confirm_desc,
                    aiBackgroundSummaryLimit.toDesc(context),
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    backfillConfirmDialogVisible = false
                    aiSettingsViewModel.enqueueUnreadSummaryBackfill { result ->
                        when (result) {
                            is PendingAiSummaryEnqueuer.BackfillResult.Enqueued -> {
                                if (result.count > 0) {
                                    context.showToast(
                                        context.getString(
                                            R.string.ai_background_summary_backfill_enqueued,
                                            result.count,
                                        )
                                    )
                                } else {
                                    context.showToast(
                                        context.getString(R.string.ai_background_summary_backfill_empty)
                                    )
                                }
                            }
                            PendingAiSummaryEnqueuer.BackfillResult.Disabled,
                            PendingAiSummaryEnqueuer.BackfillResult.Unavailable -> {
                                context.showToast(
                                    context.getString(R.string.ai_background_summary_unavailable)
                                )
                            }
                        }
                    }
                }
            ) {
                Text(text = stringResource(R.string.ai_background_summary_backfill_start))
            }
        },
        dismissButton = {
            TextButton(onClick = { backfillConfirmDialogVisible = false }) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )

    TextFieldDialog(
        textFieldState = summarizationPromptState,
        visible = promptDialogVisible,
        title = stringResource(R.string.ai_summarization_prompt),
        placeholder = stringResource(R.string.ai_summarization_prompt_hint),
        singleLine = false,
        onDismissRequest = { promptDialogVisible = false },
        onConfirm = { value: String ->
            aiSummarizationPrompt.copy(value = value).put(context, scope)
            promptDialogVisible = false
        }
    )

    TextFieldDialog(
        textFieldState = translationPromptState,
        visible = translationPromptDialogVisible,
        title = stringResource(R.string.ai_translation_prompt),
        placeholder = stringResource(R.string.ai_translation_prompt_hint),
        singleLine = false,
        onDismissRequest = { translationPromptDialogVisible = false },
        onConfirm = { value: String ->
            aiTranslationPrompt.copy(value = value).put(context, scope)
            translationPromptDialogVisible = false
        }
    )

    TextFieldDialog(
        textFieldState = commuteBriefRecommendationPromptState,
        visible = commuteBriefRecommendationPromptDialogVisible,
        title = stringResource(R.string.ai_commute_brief_recommendation_prompt),
        placeholder = stringResource(R.string.ai_commute_brief_recommendation_prompt_hint),
        singleLine = false,
        onDismissRequest = { commuteBriefRecommendationPromptDialogVisible = false },
        onConfirm = { value: String ->
            aiCommuteBriefRecommendationPrompt.copy(value = value).put(context, scope)
            commuteBriefRecommendationPromptDialogVisible = false
        }
    )

    TextFieldDialog(
        textFieldState = chatPromptState,
        visible = chatPromptDialogVisible,
        title = stringResource(R.string.ai_chat_prompt),
        placeholder = stringResource(R.string.ai_chat_prompt_hint),
        singleLine = false,
        onDismissRequest = { chatPromptDialogVisible = false },
        onConfirm = { value: String ->
            aiChatPrompt.copy(value = value).put(context, scope)
            chatPromptDialogVisible = false
        }
    )
}

@Composable
private fun AiConnectionTestStateIcon(state: AiConnectionTestState) {
    when (state) {
        AiConnectionTestState.Idle -> Unit
        AiConnectionTestState.Testing -> {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
        }
        AiConnectionTestState.Success -> {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        AiConnectionTestState.Failed -> {
            Icon(
                imageVector = Icons.Rounded.Cancel,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private enum class AiConnectionTestState {
    Idle,
    Testing,
    Success,
    Failed,
}

private fun AiBackgroundSummaryLimitPreference.toDesc(context: android.content.Context): String =
    limit?.let { context.getString(R.string.ai_background_summary_limit_value, it) }
        ?: context.getString(R.string.ai_background_summary_limit_unlimited)
