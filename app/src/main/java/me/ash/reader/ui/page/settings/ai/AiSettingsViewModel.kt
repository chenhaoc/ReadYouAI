package me.ash.reader.ui.page.settings.ai

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.ash.reader.domain.repository.AiChatRepository
import me.ash.reader.domain.repository.AiSummaryRepository
import me.ash.reader.domain.service.AccountService
import me.ash.reader.domain.service.AiSummaryPrecomputeWorker
import me.ash.reader.domain.service.PendingAiSummaryEnqueuer
import me.ash.reader.infrastructure.preference.AiConfigPreset
import me.ash.reader.infrastructure.preference.AiConfigPresetState
import me.ash.reader.infrastructure.preference.buildAiConfigPreset
import me.ash.reader.infrastructure.preference.readAiConfigPresetState
import me.ash.reader.infrastructure.preference.readLegacyAiConfigPresetState
import me.ash.reader.infrastructure.preference.updateAiConfigPresetState
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.infrastructure.net.ApiResult

private const val DEFAULT_PRESET_COPY_SUFFIX = "Copy"

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val aiSummaryRepository: AiSummaryRepository,
    private val aiChatRepository: AiChatRepository,
    private val accountService: AccountService,
    private val pendingAiSummaryEnqueuer: PendingAiSummaryEnqueuer,
    private val workManager: WorkManager,
) : ViewModel() {

    suspend fun readPresetState(context: Context): AiConfigPresetState {
        val preferences = context.dataStore.data.first()
        return preferences.readAiConfigPresetState()
            ?: preferences.readLegacyAiConfigPresetState()
            ?: AiConfigPresetState(emptyList(), "")
    }

    fun setCurrentPreset(
        context: Context,
        presetId: String,
        onComplete: () -> Unit = {},
    ) {
        viewModelScope.launch {
            context.updateAiConfigPresetState { state ->
                state.copy(currentPresetId = presetId)
            }
            onComplete()
        }
    }

    fun savePreset(
        context: Context,
        preset: AiConfigPreset,
        setAsCurrent: Boolean,
        onComplete: () -> Unit = {},
    ) {
        viewModelScope.launch {
            context.updateAiConfigPresetState(
                fallback = AiConfigPresetState(presets = listOf(preset), currentPresetId = preset.id),
            ) { state ->
                val existingIndex = state.presets.indexOfFirst { it.id == preset.id }
                val updatedPresets =
                    if (existingIndex >= 0) {
                        state.presets.toMutableList().apply { this[existingIndex] = preset }
                    } else {
                        state.presets + preset
                    }
                state.copy(
                    presets = updatedPresets,
                    currentPresetId = if (setAsCurrent || state.currentPresetId.isBlank()) preset.id else state.currentPresetId,
                )
            }
            onComplete()
        }
    }

    fun deletePreset(
        context: Context,
        presetId: String,
        onComplete: () -> Unit = {},
    ) {
        viewModelScope.launch {
            context.updateAiConfigPresetState { state ->
                val remaining = state.presets.filterNot { it.id == presetId }
                state.copy(
                    presets = remaining,
                    currentPresetId = remaining.firstOrNull()?.id.orEmpty(),
                )
            }
            onComplete()
        }
    }

    fun duplicatePreset(source: AiConfigPreset): AiConfigPreset =
        buildAiConfigPreset(
            name = "${source.name} ${DEFAULT_PRESET_COPY_SUFFIX}",
            baseUrl = source.baseUrl,
            apiKey = source.apiKey,
            model = source.model,
        )

    fun fetchModels(
        baseUrl: String,
        apiKey: String,
        onSuccess: (List<String>) -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            when (val result = aiSummaryRepository.fetchAvailableModels(baseUrl, apiKey)) {
                is ApiResult.Success -> onSuccess(result.data)
                is ApiResult.BizError -> onError(result.exception.message ?: "Business error")
                is ApiResult.NetworkError -> onError(result.exception.message ?: "Network error")
                is ApiResult.UnknownError -> onError(result.throwable.message ?: "Unknown error")
            }
        }
    }

    fun testConnection(
        baseUrl: String,
        apiKey: String,
        model: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            when (val result = aiSummaryRepository.testAiServiceConnection(baseUrl, apiKey, model)) {
                is ApiResult.Success -> onSuccess()
                is ApiResult.BizError -> onError(result.exception.message ?: "Business error")
                is ApiResult.NetworkError -> onError(result.exception.message ?: "Network error")
                is ApiResult.UnknownError -> onError(result.throwable.message ?: "Unknown error")
            }
        }
    }

    fun testWebSearch(
        baseUrl: String,
        apiKey: String,
        model: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            when (val result = aiChatRepository.testWebSearch(baseUrl, apiKey, model)) {
                is ApiResult.Success -> onSuccess()
                is ApiResult.BizError -> onError(result.exception.message ?: "Business error")
                is ApiResult.NetworkError -> onError(result.exception.message ?: "Network error")
                is ApiResult.UnknownError -> onError(result.throwable.message ?: "Unknown error")
            }
        }
    }

    fun enqueueUnreadSummaryBackfill(onResult: (PendingAiSummaryEnqueuer.BackfillResult) -> Unit) {
        viewModelScope.launch {
            val accountId = accountService.getCurrentAccountId()
            val result =
                pendingAiSummaryEnqueuer.enqueueUnreadBackfill(
                    accountId = accountId,
                    requireBackfillOnSync = false,
                )
            if (result is PendingAiSummaryEnqueuer.BackfillResult.Enqueued && result.count > 0) {
                AiSummaryPrecomputeWorker.enqueueOneTimeWork(workManager, accountId)
            }
            onResult(result)
        }
    }
}
