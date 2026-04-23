package me.ash.reader.domain.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import me.ash.reader.infrastructure.rss.ReaderCacheHelper

@HiltWorker
class ReaderWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val rssService: RssService,
    private val cacheHelper: ReaderCacheHelper,
    private val accountService: AccountService,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val accountId = inputData.getInt(SyncWorker.INPUT_ACCOUNT_ID, -1)
        require(accountId != -1)
        val account = accountService.getAccountById(accountId) ?: return Result.failure()
        val semaphore = Semaphore(2)

        val deferredList =
            withContext(Dispatchers.IO) {
                val rssRepository = rssService.get(account.type.id)
                val articleList = rssRepository.queryUnreadFullContentArticles(accountId)
                articleList.map {
                    async {
                        semaphore.withPermit {
                            cacheHelper.checkOrFetchFullContent(it, accountId = accountId)
                        }
                    }
                }
            }

        return if (deferredList.awaitAll().any { !it }) Result.retry() else Result.success()
    }
}
