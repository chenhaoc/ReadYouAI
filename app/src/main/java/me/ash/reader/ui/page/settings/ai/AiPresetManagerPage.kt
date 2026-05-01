package me.ash.reader.ui.page.settings.ai

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import me.ash.reader.R
import me.ash.reader.infrastructure.preference.AiBaseUrlPreference
import me.ash.reader.infrastructure.preference.AiConfigPreset
import me.ash.reader.infrastructure.preference.buildAiConfigPreset
import me.ash.reader.infrastructure.preference.summary
import me.ash.reader.infrastructure.preference.LocalSettings
import me.ash.reader.ui.component.base.ClipboardTextField
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYDialog
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.component.base.RadioDialog
import me.ash.reader.ui.component.base.RadioDialogOption
import me.ash.reader.ui.ext.showToast
import me.ash.reader.ui.page.settings.SettingItem
import me.ash.reader.ui.theme.palette.onLight

private val AiPresetEditorFieldTopSpacing = 0.dp
private val AiPresetEditorSeparatorToActionSpacing = 28.dp
private val AiPresetEditorActionSectionSpacing = 10.dp
private val AiPresetEditorActionRowVerticalPadding = 2.dp
private val AiPresetEditorTrailingButtonContentPadding = PaddingValues(start = 12.dp, top = 0.dp, end = 12.dp, bottom = 0.dp)
private val AiPresetEditorActionButtonsOffset = 12.dp

@Composable
fun AiPresetManagerPage(
    aiSettingsViewModel: AiSettingsViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    var editingPreset by remember { mutableStateOf<AiConfigPreset?>(null) }
    var deletingPreset by remember { mutableStateOf<AiConfigPreset?>(null) }
    var expandedPresetMenuId by remember { mutableStateOf<String?>(null) }

    RYScaffold(
        containerColor = MaterialTheme.colorScheme.surface onLight MaterialTheme.colorScheme.inverseOnSurface,
        navigationIcon = {
            FeedbackIconButton(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onBack,
            )
        },
        actions = {
            FeedbackIconButton(
                imageVector = Icons.Outlined.Add,
                contentDescription = stringResource(R.string.ai_preset_add),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = {
                    editingPreset = buildAiConfigPreset(
                        name = "",
                        baseUrl = AiBaseUrlPreference.DEFAULT_BASE_URL,
                        apiKey = "",
                        model = "",
                    )
                },
            )
        },
        content = {
            LazyColumn {
                item {
                    DisplayText(
                        text = stringResource(R.string.ai_configuration_manager),
                        desc = stringResource(R.string.ai_manage_configurations_desc),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (settings.aiConfigPresets.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.ai_configuration_empty),
                            modifier = Modifier.padding(horizontal = 24.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                settings.aiConfigPresets.forEach { preset ->
                    item(preset.id) {
                        SettingItem(
                            title = preset.name,
                            desc = preset.summary(),
                            titleContent = {
                                Text(
                                    text = preset.name,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = if (preset.id == settings.aiCurrentPresetId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            onClick = {},
                        ) {
                            Box {
                                TextButton(onClick = { expandedPresetMenuId = preset.id }) {
                                    Text(text = stringResource(R.string.manage))
                                    Icon(
                                        imageVector = Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = stringResource(R.string.manage),
                                    )
                                }
                                DropdownMenu(
                                    expanded = expandedPresetMenuId == preset.id,
                                    onDismissRequest = { expandedPresetMenuId = null },
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (preset.id == settings.aiCurrentPresetId) {
                                                    stringResource(R.string.ai_configuration_currently_selected)
                                                } else {
                                                    stringResource(R.string.ai_configuration_switch)
                                                }
                                            )
                                        },
                                        onClick = {
                                            expandedPresetMenuId = null
                                            aiSettingsViewModel.setCurrentPreset(context, preset.id) {
                                                context.showToast(context.getString(R.string.ai_preset_switched))
                                            }
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(text = stringResource(R.string.ai_configuration_edit)) },
                                        onClick = {
                                            expandedPresetMenuId = null
                                            editingPreset = preset
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(text = stringResource(R.string.ai_configuration_copy)) },
                                        onClick = {
                                            expandedPresetMenuId = null
                                            editingPreset = aiSettingsViewModel.duplicatePreset(preset)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(text = stringResource(R.string.ai_configuration_delete)) },
                                        onClick = {
                                            expandedPresetMenuId = null
                                            deletingPreset = preset
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        },
    )

    editingPreset?.let { preset ->
        AiPresetEditorDialog(
            preset = preset,
            isCurrent = preset.id == settings.aiCurrentPresetId,
            onDismiss = { editingPreset = null },
            onSave = { updatedPreset, setAsCurrent ->
                aiSettingsViewModel.savePreset(context, updatedPreset, setAsCurrent) {
                    editingPreset = null
                    context.showToast(context.getString(R.string.ai_preset_saved))
                }
            },
            aiSettingsViewModel = aiSettingsViewModel,
        )
    }

    deletingPreset?.let { preset ->
        RYDialog(
            visible = true,
            onDismissRequest = { deletingPreset = null },
            title = { Text(stringResource(R.string.ai_configuration_delete)) },
            text = { Text(stringResource(R.string.ai_configuration_delete_confirm, preset.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        aiSettingsViewModel.deletePreset(context, preset.id) {
                            deletingPreset = null
                            context.showToast(context.getString(R.string.ai_preset_deleted))
                        }
                    },
                ) { Text(text = stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingPreset = null }) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun AiPresetEditorDialog(
    preset: AiConfigPreset,
    isCurrent: Boolean,
    onDismiss: () -> Unit,
    onSave: (AiConfigPreset, Boolean) -> Unit,
    aiSettingsViewModel: AiSettingsViewModel,
) {
    val context = LocalContext.current
    val nameState = rememberTextFieldState(preset.name)
    val baseUrlState = rememberTextFieldState(preset.baseUrl)
    val apiKeyState = rememberTextFieldState(preset.apiKey)
    val modelState = rememberTextFieldState(preset.model)
    var setAsCurrent by remember { mutableStateOf(isCurrent) }
    val fetchedModels = remember { mutableStateListOf<String>() }
    var loadingModels by remember { mutableStateOf(false) }
    var fetchError by remember { mutableStateOf<String?>(null) }
    var modelPickerVisible by remember { mutableStateOf(false) }
    var testConnectionState by remember { mutableStateOf(AiEditorConnectionTestState.Idle) }
    var testWebSearchState by remember { mutableStateOf(AiEditorConnectionTestState.Idle) }
    val editorTextStyle = MaterialTheme.typography.bodyLarge

    RYDialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.ai_configuration_edit)) },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
            ) {
                Text(text = stringResource(R.string.ai_configuration_name), style = editorTextStyle)
                ClipboardTextField(
                    state = nameState,
                    placeholder = stringResource(R.string.ai_configuration_name_hint),
                    topSpacing = AiPresetEditorFieldTopSpacing,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(text = stringResource(R.string.ai_base_url), style = editorTextStyle)
                ClipboardTextField(
                    state = baseUrlState,
                    placeholder = stringResource(R.string.ai_base_url_hint),
                    topSpacing = AiPresetEditorFieldTopSpacing,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = stringResource(R.string.ai_api_key), style = editorTextStyle)
                    if (apiKeyState.text.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.clear),
                            color = MaterialTheme.colorScheme.primary,
                            style = editorTextStyle,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .clickable { apiKeyState.clearText() },
                        )
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                }
                ClipboardTextField(
                    state = apiKeyState,
                    placeholder = stringResource(R.string.ai_api_key_hint),
                    isPassword = true,
                    topSpacing = AiPresetEditorFieldTopSpacing,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(text = stringResource(R.string.ai_model), style = editorTextStyle)
                ClipboardTextField(
                    state = modelState,
                    placeholder = stringResource(R.string.ai_preset_manual_model_hint),
                    topSpacing = AiPresetEditorFieldTopSpacing,
                    bottomSpacing = 0.dp,
                )
                if (fetchError != null) {
                    Spacer(modifier = Modifier.height(AiPresetEditorActionSectionSpacing))
                    Text(
                        text = fetchError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = editorTextStyle,
                    )
                }
                Spacer(modifier = Modifier.height(AiPresetEditorSeparatorToActionSpacing))
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    AiPresetEditorActionRow(
                        text = stringResource(R.string.ai_fetch_models_trigger),
                        enabled = !loadingModels,
                        onClick = {
                            val baseUrl = baseUrlState.text.toString().trim()
                            val apiKey = apiKeyState.text.toString().trim()
                            if (baseUrl.isBlank() || apiKey.isBlank()) {
                                fetchError = "Base URL or API key is empty"
                                return@AiPresetEditorActionRow
                            }
                            loadingModels = true
                            fetchError = null
                            fetchedModels.clear()
                            aiSettingsViewModel.fetchModels(
                                baseUrl = baseUrl,
                                apiKey = apiKey,
                                onSuccess = {
                                    fetchedModels.addAll(it)
                                    loadingModels = false
                                    modelPickerVisible = true
                                },
                                onError = {
                                    fetchError = it
                                    loadingModels = false
                                },
                            )
                        },
                        trailingContent = {
                            if (loadingModels) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        },
                    )
                    Spacer(modifier = Modifier.height(AiPresetEditorActionSectionSpacing))
                    AiPresetEditorActionRow(
                        text = stringResource(R.string.ai_test_connection),
                        enabled = testConnectionState != AiEditorConnectionTestState.Testing,
                        onClick = {
                            val baseUrl = baseUrlState.text.toString().trim()
                            val apiKey = apiKeyState.text.toString().trim()
                            val model = modelState.text.toString().trim()
                            testConnectionState = AiEditorConnectionTestState.Testing
                            aiSettingsViewModel.testConnection(
                                baseUrl = baseUrl,
                                apiKey = apiKey,
                                model = model,
                                onSuccess = {
                                    testConnectionState = AiEditorConnectionTestState.Success
                                    context.showToast(context.getString(R.string.ai_test_connection_success))
                                },
                                onError = {
                                    testConnectionState = AiEditorConnectionTestState.Failed
                                    context.showToast(context.getString(R.string.ai_test_connection_failed, it))
                                },
                            )
                        },
                        trailingContent = {
                            AiEditorConnectionTestStateIcon(state = testConnectionState)
                        },
                    )
                    Spacer(modifier = Modifier.height(AiPresetEditorActionSectionSpacing))
                    AiPresetEditorActionRow(
                        text = stringResource(R.string.ai_test_web_search),
                        enabled = testWebSearchState != AiEditorConnectionTestState.Testing,
                        onClick = {
                            val baseUrl = baseUrlState.text.toString().trim()
                            val apiKey = apiKeyState.text.toString().trim()
                            val model = modelState.text.toString().trim()
                            testWebSearchState = AiEditorConnectionTestState.Testing
                            aiSettingsViewModel.testWebSearch(
                                baseUrl = baseUrl,
                                apiKey = apiKey,
                                model = model,
                                onSuccess = {
                                    testWebSearchState = AiEditorConnectionTestState.Success
                                    context.showToast(context.getString(R.string.ai_test_web_search_success))
                                },
                                onError = {
                                    testWebSearchState = AiEditorConnectionTestState.Failed
                                    context.showToast(context.getString(R.string.ai_test_web_search_failed, it))
                                },
                            )
                        },
                        trailingContent = {
                            AiEditorConnectionTestStateIcon(state = testWebSearchState)
                        },
                    )
                    Spacer(modifier = Modifier.height(AiPresetEditorActionSectionSpacing))
                    Row(
                        modifier = Modifier.padding(vertical = AiPresetEditorActionRowVerticalPadding),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = setAsCurrent, onCheckedChange = { setAsCurrent = it })
                        Text(
                            text = stringResource(R.string.ai_preset_apply_after_save),
                            style = editorTextStyle,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.offset(y = -AiPresetEditorActionButtonsOffset),
                contentPadding = AiPresetEditorTrailingButtonContentPadding,
                onClick = {
                    onSave(
                        buildAiConfigPreset(
                            id = preset.id,
                            name = nameState.text.toString(),
                            baseUrl = baseUrlState.text.toString(),
                            apiKey = apiKeyState.text.toString(),
                            model = modelState.text.toString(),
                        ),
                        setAsCurrent,
                    )
                },
            ) { Text(text = stringResource(R.string.confirm), style = editorTextStyle) }
        },
        dismissButton = {
            TextButton(
                modifier = Modifier.offset(y = -AiPresetEditorActionButtonsOffset),
                contentPadding = AiPresetEditorTrailingButtonContentPadding,
                onClick = onDismiss,
            ) { Text(text = stringResource(R.string.cancel), style = editorTextStyle) }
        },
    )

    if (fetchedModels.isNotEmpty()) {
        RadioDialog(
            visible = modelPickerVisible,
            title = stringResource(R.string.ai_model),
            options = fetchedModels.map { model ->
                RadioDialogOption(
                    text = model,
                    selected = model == modelState.text.toString(),
                ) {
                    modelState.setTextAndPlaceCursorAtEnd(model)
                    modelPickerVisible = false
                }
            },
            onDismissRequest = { modelPickerVisible = false },
        )
    }
}

@Composable
private fun AiPresetEditorActionRow(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = AiPresetEditorActionRowVerticalPadding),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
        )
        trailingContent?.invoke()
    }
}

@Composable
private fun AiEditorConnectionTestStateIcon(state: AiEditorConnectionTestState) {
    when (state) {
        AiEditorConnectionTestState.Idle -> Unit
        AiEditorConnectionTestState.Testing -> {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
        }
        AiEditorConnectionTestState.Success -> {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        AiEditorConnectionTestState.Failed -> {
            Icon(
                imageVector = Icons.Rounded.Cancel,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private enum class AiEditorConnectionTestState {
    Idle,
    Testing,
    Success,
    Failed,
}
