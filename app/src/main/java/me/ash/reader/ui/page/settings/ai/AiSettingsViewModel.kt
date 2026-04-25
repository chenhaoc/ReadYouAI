package me.ash.reader.ui.page.settings.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import me.ash.reader.domain.repository.AiSummaryRepository
import me.ash.reader.domain.service.AccountService
import me.ash.reader.domain.service.AiSummaryPrecomputeWorker
import me.ash.reader.domain.service.PendingAiSummaryEnqueuer
import me.ash.reader.infrastructure.net.ApiResult

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val aiSummaryRepository: AiSummaryRepository,
    private val accountService: AccountService,
    private val pendingAiSummaryEnqueuer: PendingAiSummaryEnqueuer,
    private val workManager: WorkManager,
) : ViewModel() {

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
