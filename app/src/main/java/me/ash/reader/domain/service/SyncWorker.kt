package me.ash.reader.domain.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import me.ash.reader.domain.model.account.Account
import me.ash.reader.infrastructure.rss.ReaderCacheHelper

@HiltWorker
class SyncWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val rssService: RssService,
    private val accountService: AccountService,
    private val readerCacheHelper: ReaderCacheHelper,
    private val workManager: WorkManager,
    private val pendingAiSummaryEnqueuer: PendingAiSummaryEnqueuer,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val data = inputData
        val accountId = data.getInt(INPUT_ACCOUNT_ID, -1)
        require(accountId != -1)
        val feedId = data.getString("feedId")
        val groupId = data.getString("groupId")
        val account = accountService.getAccountById(accountId) ?: return Result.failure()
        val rssRepository = rssService.get(account.type.id)

        val result = rssRepository.sync(accountId = accountId, feedId = feedId, groupId = groupId)

        if (result is Result.Success) {
            pendingAiSummaryEnqueuer.enqueueUnreadBackfill(
                accountId = accountId,
                requireBackfillOnSync = true,
            )
            rssRepository.clearKeepArchivedArticles(accountId).forEach {
                readerCacheHelper.deleteCacheFor(articleId = it.id, accountId = it.accountId)
            }
            val workerInputData =
                workDataOf(
                    INPUT_ACCOUNT_ID to accountId,
                    "feedId" to feedId,
                    "groupId" to groupId,
                )
            val readerWork =
                OneTimeWorkRequestBuilder<ReaderWorker>()
                    .addTag(READER_TAG)
                    .addTag(ONETIME_WORK_TAG)
                    .setInputData(workerInputData)
                    .setBackoffCriteria(
                        backoffPolicy = BackoffPolicy.EXPONENTIAL,
                        backoffDelay = 30,
                        timeUnit = TimeUnit.SECONDS,
                    )
                    .build()
            val aiSummaryWork =
                OneTimeWorkRequestBuilder<AiSummaryPrecomputeWorker>()
                    .addTag(ONETIME_WORK_TAG)
                    .setInputData(workerInputData)
                    .setBackoffCriteria(
                        backoffPolicy = BackoffPolicy.EXPONENTIAL,
                        backoffDelay = 30,
                        timeUnit = TimeUnit.SECONDS,
                    )
                    .build()
            val widgetWork = OneTimeWorkRequestBuilder<WidgetUpdateWorker>().build()

            workManager
                .beginUniqueWork(
                    uniqueWorkName = postSyncWorkName(accountId),
                    existingWorkPolicy = ExistingWorkPolicy.KEEP,
                    readerWork,
                )
                .then(listOf(aiSummaryWork, widgetWork))
                .enqueue()
        }

        return result
    }

    companion object {
        private const val SYNC_WORK_NAME_PERIODIC = "ReadYou"
        @Deprecated("do not use")
        private const val READER_WORK_NAME_PERIODIC = "FETCH_FULL_CONTENT_PERIODIC"
        private const val POST_SYNC_WORK_NAME = "POST_SYNC_WORK"
        const val INPUT_ACCOUNT_ID = "accountId"

        private const val SYNC_ONETIME_NAME = "SYNC_ONETIME"

        const val SYNC_TAG = "SYNC_TAG"
        const val READER_TAG = "READER_TAG"
        const val ONETIME_WORK_TAG = "ONETIME_WORK_TAG"
        const val PERIODIC_WORK_TAG = "PERIODIC_WORK_TAG"

        internal fun postSyncWorkName(accountId: Int): String = "$POST_SYNC_WORK_NAME:$accountId"

        fun cancelOneTimeWork(workManager: WorkManager) {
            workManager.cancelUniqueWork(SYNC_ONETIME_NAME)
        }

        fun cancelPeriodicWork(workManager: WorkManager) {
            workManager.cancelUniqueWork(SYNC_WORK_NAME_PERIODIC)
            workManager.cancelUniqueWork(READER_WORK_NAME_PERIODIC)
        }

        fun enqueueOneTimeWork(workManager: WorkManager, inputData: Data = workDataOf()) {
            workManager
                .beginUniqueWork(
                    SYNC_ONETIME_NAME,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<SyncWorker>()
                        .addTag(SYNC_TAG)
                        .addTag(ONETIME_WORK_TAG)
                        .setInputData(inputData)
                        .build(),
                )
                .enqueue()
        }

        fun enqueuePeriodicWork(account: Account, workManager: WorkManager) {
            val syncInterval = account.syncInterval
            val syncOnlyWhenCharging = account.syncOnlyWhenCharging
            val syncOnlyOnWiFi = account.syncOnlyOnWiFi
            val workState =
                workManager
                    .getWorkInfosForUniqueWork(SYNC_WORK_NAME_PERIODIC)
                    .get()
                    .firstOrNull()
                    ?.state

            val policy =
                if (workState == WorkInfo.State.ENQUEUED || workState == WorkInfo.State.RUNNING)
                    ExistingPeriodicWorkPolicy.UPDATE
                else ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE

            workManager.enqueueUniquePeriodicWork(
                SYNC_WORK_NAME_PERIODIC,
                policy,
                PeriodicWorkRequestBuilder<SyncWorker>(syncInterval.value, TimeUnit.MINUTES)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiresCharging(syncOnlyWhenCharging.value)
                            .setRequiredNetworkType(
                                if (syncOnlyOnWiFi.value) NetworkType.UNMETERED
                                else NetworkType.CONNECTED
                            )
                            .build()
                    )
                    .setBackoffCriteria(
                        backoffPolicy = BackoffPolicy.EXPONENTIAL,
                        backoffDelay = 30,
                        timeUnit = TimeUnit.SECONDS,
                    )
                    .setInputData(workDataOf(INPUT_ACCOUNT_ID to account.id))
                    .addTag(SYNC_TAG)
                    .addTag(PERIODIC_WORK_TAG)
                    .setInitialDelay(syncInterval.value, TimeUnit.MINUTES)
                    .build(),
            )

            workManager.cancelUniqueWork(READER_WORK_NAME_PERIODIC)
        }
    }
}
