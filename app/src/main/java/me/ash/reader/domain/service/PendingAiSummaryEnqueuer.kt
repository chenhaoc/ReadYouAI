package me.ash.reader.domain.service

import java.util.Date
import javax.inject.Inject
import me.ash.reader.domain.model.ai.PendingAiSummaryTask
import me.ash.reader.domain.model.article.Article
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.domain.repository.PendingAiSummaryTaskDao
import me.ash.reader.infrastructure.preference.SettingsProvider

class PendingAiSummaryEnqueuer
@Inject
constructor(
    private val articleDao: ArticleDao,
    private val feedDao: FeedDao,
    private val pendingAiSummaryTaskDao: PendingAiSummaryTaskDao,
    private val settingsProvider: SettingsProvider,
) {
    suspend fun enqueue(accountId: Int, articles: List<Article>) {
        if (articles.isEmpty()) return

        val settings = settingsProvider.settings
        if (!settings.canPrecomputeAiSummary()) return

        val autoSummaryFeedIds =
            feedDao
                .queryByIds(articles.map { it.feedId }.distinct())
                .filter { it.isAutoSummary }
                .map { it.id }
                .toSet()
        if (autoSummaryFeedIds.isEmpty()) return

        val candidates =
            articles
                .asSequence()
                .filter { it.feedId in autoSummaryFeedIds && it.aiSummary.isNullOrBlank() }
                .sortedByDescending { it.date }
                .limitTo(settings.aiBackgroundSummaryLimit.limit)
                .toList()
        enqueueTasks(accountId = accountId, articles = candidates)
    }

    suspend fun enqueueUnreadBackfill(
        accountId: Int,
        requireBackfillOnSync: Boolean,
    ): BackfillResult {
        val settings = settingsProvider.settings
        if (!settings.canPrecomputeAiSummary()) return BackfillResult.Unavailable
        if (requireBackfillOnSync && !settings.aiBackgroundSummaryBackfillOnSync.value) {
            return BackfillResult.Disabled
        }

        val limit = settings.aiBackgroundSummaryLimit.limit
        val candidates =
            articleDao.queryUnreadAutoSummaryMissingAiSummary(
                accountId = accountId,
                limit = limit ?: Int.MAX_VALUE,
            )
        enqueueTasks(accountId = accountId, articles = candidates)
        return BackfillResult.Enqueued(candidates.size)
    }

    private suspend fun enqueueTasks(accountId: Int, articles: List<Article>) {
        if (articles.isEmpty()) return
        val now = Date()
        val tasks =
            articles.mapIndexed { index, article ->
                PendingAiSummaryTask(
                    articleId = article.id,
                    accountId = accountId,
                    createdAt = Date(now.time + index),
                )
            }
        pendingAiSummaryTaskDao.insert(tasks)
    }

    private fun me.ash.reader.infrastructure.preference.Settings.canPrecomputeAiSummary(): Boolean =
        aiBackgroundSummary.value && aiBaseUrl.value.isNotBlank() && aiApiKey.value.isNotBlank()

    private fun Sequence<Article>.limitTo(limit: Int?): Sequence<Article> =
        if (limit == null) this else take(limit)

    sealed interface BackfillResult {
        data class Enqueued(val count: Int) : BackfillResult
        data object Disabled : BackfillResult
        data object Unavailable : BackfillResult
    }
}
