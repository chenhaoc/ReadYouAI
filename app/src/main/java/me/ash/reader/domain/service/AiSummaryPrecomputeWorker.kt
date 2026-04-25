package me.ash.reader.domain.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.Date
import java.util.concurrent.TimeUnit
import me.ash.reader.domain.model.ai.PendingAiSummaryTask
import me.ash.reader.domain.repository.AiSummaryRepository
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.repository.PendingAiSummaryTaskDao
import me.ash.reader.infrastructure.preference.Settings
import me.ash.reader.infrastructure.preference.SettingsProvider
import me.ash.reader.infrastructure.rss.ReaderCacheHelper
import me.ash.reader.ui.page.home.reading.resolveAiSummarizationPrompt
import timber.log.Timber

@HiltWorker
class AiSummaryPrecomputeWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val articleDao: ArticleDao,
    private val pendingAiSummaryTaskDao: PendingAiSummaryTaskDao,
    private val aiSummaryRepository: AiSummaryRepository,
    private val settingsProvider: SettingsProvider,
    private val readerCacheHelper: ReaderCacheHelper,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val accountId = inputData.getInt(SyncWorker.INPUT_ACCOUNT_ID, -1)
        require(accountId != -1)

        val settings = settingsProvider.settings
        if (
            !settings.aiBackgroundSummary.value ||
                settings.aiBaseUrl.value.isBlank() ||
                settings.aiApiKey.value.isBlank()
        ) {
            pendingAiSummaryTaskDao.deleteByAccountId(accountId)
            return Result.success()
        }

        var remainingTasks = settings.aiBackgroundSummaryLimit.limit ?: Int.MAX_VALUE
        while (remainingTasks > 0) {
            val tasks =
                pendingAiSummaryTaskDao.queryRunnableByAccountId(
                    accountId = accountId,
                    now = Date(),
                    limit = minOf(TASK_BATCH_SIZE, remainingTasks),
                )
            if (tasks.isEmpty()) break
            remainingTasks -= tasks.size

            val semaphore = Semaphore(MAX_PARALLELISM)
            val outcomes =
                coroutineScope {
                    tasks.map { task ->
                        async {
                            semaphore.withPermit {
                                processTask(task = task, settings = settings)
                            }
                        }
                    }.awaitAll()
                }

            val completedTaskIds = outcomes.filter { it.deleteTask }.map { it.articleId }
            if (completedTaskIds.isNotEmpty()) {
                pendingAiSummaryTaskDao.deleteByArticleIds(completedTaskIds)
            }

            val retryOutcomes = outcomes.filter { it.retryTask }
            retryOutcomes.forEach { outcome ->
                val nextAttemptCount = outcome.attemptCount + 1
                if (nextAttemptCount >= MAX_RETRY_ATTEMPTS) {
                    Timber.w(
                        "Drop background AI summary task after %s attempts for article %s",
                        nextAttemptCount,
                        outcome.articleId,
                    )
                    pendingAiSummaryTaskDao.deleteByArticleIds(listOf(outcome.articleId))
                } else {
                    val now = Date()
                    pendingAiSummaryTaskDao.scheduleRetry(
                        articleId = outcome.articleId,
                        lastAttemptAt = now,
                        nextRunAt = Date(now.time + retryDelayMillis(nextAttemptCount)),
                    )
                }
            }
            if (retryOutcomes.size == tasks.size) break
        }

        return Result.success()
    }

    private suspend fun processTask(
        task: PendingAiSummaryTask,
        settings: Settings,
    ): TaskOutcome {
        val articleWithFeed = articleDao.queryById(task.articleId)
        if (articleWithFeed == null) {
            return TaskOutcome(articleId = task.articleId, deleteTask = true)
        }

        if (!articleWithFeed.feed.isAutoSummary) {
            return TaskOutcome(articleId = task.articleId, deleteTask = true)
        }

        if (!articleWithFeed.article.aiSummary.isNullOrBlank()) {
            return TaskOutcome(articleId = task.articleId, deleteTask = true)
        }

        val articleContent =
            readerCacheHelper
                .readFullContent(articleId = task.articleId, accountId = task.accountId)
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: articleWithFeed.article.rawDescription.takeIf { it.isNotBlank() }
                ?: articleWithFeed.article.shortDescription.takeIf { it.isNotBlank() }
                ?: return TaskOutcome(articleId = task.articleId, deleteTask = true)

        return when (
            val result =
                aiSummaryRepository.summarizeArticle(
                    baseUrl = settings.aiBaseUrl.value,
                    apiKey = settings.aiApiKey.value,
                    model = settings.aiModel.value.ifEmpty { "gpt-3.5-turbo" },
                    prompt = resolveAiSummarizationPrompt(settings.aiSummarizationPrompt.value),
                    articleContent = articleContent,
                )
        ) {
            is me.ash.reader.infrastructure.net.ApiResult.Success -> {
                articleDao.updateAiSummary(task.articleId, result.data)
                TaskOutcome(articleId = task.articleId, deleteTask = true)
            }

            is me.ash.reader.infrastructure.net.ApiResult.BizError -> {
                Timber.w(
                    result.exception,
                    "Background AI summary biz error for article %s",
                    task.articleId,
                )
                TaskOutcome(articleId = task.articleId, deleteTask = true)
            }

            is me.ash.reader.infrastructure.net.ApiResult.NetworkError -> {
                Timber.w(
                    result.exception,
                    "Background AI summary network error for article %s",
                    task.articleId,
                )
                TaskOutcome(
                    articleId = task.articleId,
                    attemptCount = task.attemptCount,
                    retryTask = true,
                )
            }

            is me.ash.reader.infrastructure.net.ApiResult.UnknownError -> {
                Timber.w(
                    result.throwable,
                    "Background AI summary unknown error for article %s",
                    task.articleId,
                )
                TaskOutcome(
                    articleId = task.articleId,
                    attemptCount = task.attemptCount,
                    retryTask = true,
                )
            }
        }
    }

    private data class TaskOutcome(
        val articleId: String,
        val attemptCount: Int = 0,
        val deleteTask: Boolean = false,
        val retryTask: Boolean = false,
    )

    companion object {
        private const val MAX_PARALLELISM = 3
        private const val TASK_BATCH_SIZE = 12
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val INITIAL_RETRY_DELAY_MS = 15 * 60 * 1000L
        private const val MANUAL_WORK_NAME = "AI_SUMMARY_PRECOMPUTE"

        fun enqueueOneTimeWork(workManager: WorkManager, accountId: Int) {
            val inputData = workDataOf(SyncWorker.INPUT_ACCOUNT_ID to accountId)
            val request =
                OneTimeWorkRequestBuilder<AiSummaryPrecomputeWorker>()
                    .addTag(SyncWorker.ONETIME_WORK_TAG)
                    .setInputData(inputData)
                    .setBackoffCriteria(
                        backoffPolicy = androidx.work.BackoffPolicy.EXPONENTIAL,
                        backoffDelay = 30,
                        timeUnit = TimeUnit.SECONDS,
                    )
                    .build()
            workManager.enqueueUniqueWork(
                "$MANUAL_WORK_NAME:$accountId",
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        internal fun retryDelayMillis(nextAttemptCount: Int): Long =
            INITIAL_RETRY_DELAY_MS shl (nextAttemptCount - 1).coerceAtLeast(0)
    }
}
