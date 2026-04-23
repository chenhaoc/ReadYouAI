package me.ash.reader.domain.service

import java.util.Date
import javax.inject.Inject
import me.ash.reader.domain.model.ai.PendingAiSummaryTask
import me.ash.reader.domain.model.article.Article
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.domain.repository.PendingAiSummaryTaskDao
import me.ash.reader.infrastructure.preference.SettingsProvider

class PendingAiSummaryEnqueuer
@Inject
constructor(
    private val feedDao: FeedDao,
    private val pendingAiSummaryTaskDao: PendingAiSummaryTaskDao,
    private val settingsProvider: SettingsProvider,
) {
    suspend fun enqueue(accountId: Int, articles: List<Article>) {
        if (articles.isEmpty()) return

        val settings = settingsProvider.settings
        if (!settings.aiBackgroundSummary.value) return
        if (settings.aiBaseUrl.value.isBlank() || settings.aiApiKey.value.isBlank()) return

        val autoSummaryFeedIds =
            feedDao
                .queryByIds(articles.map { it.feedId }.distinct())
                .filter { it.isAutoSummary }
                .map { it.id }
                .toSet()
        if (autoSummaryFeedIds.isEmpty()) return

        val tasks =
            articles
                .filter { it.feedId in autoSummaryFeedIds && it.aiSummary.isNullOrBlank() }
                .map {
                    PendingAiSummaryTask(
                        articleId = it.id,
                        accountId = accountId,
                        createdAt = Date(),
                    )
                }
        if (tasks.isEmpty()) return

        pendingAiSummaryTaskDao.insert(tasks)
    }
}
