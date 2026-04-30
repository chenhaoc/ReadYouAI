package me.ash.reader.infrastructure.android.ttsqueue

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import me.ash.reader.domain.model.article.ArticleWithFeed
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.service.AccountService
import me.ash.reader.domain.service.RssService
import me.ash.reader.infrastructure.android.TextToSpeechManager
import me.ash.reader.infrastructure.android.htmlSegmentCharCounts
import me.ash.reader.infrastructure.html.VideoNoiseCleaner
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class DataStoreTtsQueueSnapshotStore
@Inject
constructor(
    @ApplicationContext private val context: Context,
) : TtsQueueSnapshotStore {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun readSnapshot(): TtsQueueSnapshot? {
        val raw = context.dataStore.data.first()[stringPreferencesKey(DataStoreKey.ttsQueueSnapshot)]
            ?: return null
        if (raw.isBlank()) return null
        return runCatching { json.decodeFromString<TtsQueueSnapshot>(raw) }.getOrNull()
    }

    override suspend fun writeSnapshot(snapshot: TtsQueueSnapshot?) {
        context.dataStore.put(
            DataStoreKey.ttsQueueSnapshot,
            snapshot?.let(json::encodeToString).orEmpty(),
        )
    }
}

@Singleton
class ArticleDaoTtsQueueArticleRepository
@Inject
constructor(
    private val articleDao: ArticleDao,
    private val accountService: AccountService,
    private val rssService: RssService,
) : TtsQueueArticleRepository {
    override suspend fun get(item: TtsQueueItem): TtsQueuePlayableArticle? {
        if (item.contentType == TtsQueueContentType.AiSummary) {
            val articleWithFeed = articleDao.queryById(item.articleId) ?: return null
            val playableHtml = item.summaryHtmlContent?.takeIf { it.isNotBlank() } ?: return null
            return TtsQueuePlayableArticle(
                item =
                    item.copy(
                        publishedAtMillis = item.publishedAtMillis ?: articleWithFeed.article.date.time,
                    ),
                htmlContent = playableHtml,
                segmentCharCounts = htmlSegmentCharCounts(playableHtml),
            )
        }

        return articleDao.queryById(item.articleId)?.let { articleWithFeed ->
            val playableHtml =
                resolvePlayableHtmlContent(
                    rawDescription = articleWithFeed.article.rawDescription,
                    shortDescription = articleWithFeed.article.shortDescription,
                    title = articleWithFeed.article.title,
                ) ?: item.htmlContent ?: return null
            TtsQueuePlayableArticle(
                item = articleWithFeed.toQueueItem(),
                htmlContent = playableHtml,
                segmentCharCounts = htmlSegmentCharCounts(playableHtml),
            )
        }
    }

    override suspend fun getById(articleId: String): TtsQueuePlayableArticle? {
        return articleDao.queryById(articleId)?.let { articleWithFeed ->
            val playableHtml =
                resolvePlayableHtmlContent(
                    rawDescription = articleWithFeed.article.rawDescription,
                    shortDescription = articleWithFeed.article.shortDescription,
                    title = articleWithFeed.article.title,
                ) ?: return null
            TtsQueuePlayableArticle(
                item = articleWithFeed.toQueueItem(),
                htmlContent = playableHtml,
                segmentCharCounts = htmlSegmentCharCounts(playableHtml),
            )
        }
    }

    override suspend fun isUnread(articleId: String): Boolean =
        articleDao.queryIsUnreadByArticleId(articleId) ?: false

    override suspend fun markAsRead(articleId: String) {
        rssService.get().markAsRead(
            groupId = null,
            feedId = null,
            articleId = articleId,
            before = null,
            isUnread = false,
        )
    }

    override fun observeIsStarred(articleId: String): Flow<Boolean> =
        articleDao.queryIsStarredByArticleId(articleId).map { it == true }

    override suspend fun markAsStarred(articleId: String, isStarred: Boolean) {
        rssService.get().markAsStarred(articleId = articleId, isStarred = isStarred)
    }
}

@Singleton
class TextToSpeechQueuePlaybackClient
@Inject
constructor(
    private val textToSpeechManager: TextToSpeechManager,
) : TtsQueuePlaybackClient {
    override val events: Flow<TtsPlaybackEvent> =
        textToSpeechManager.events.map { event ->
            when (event) {
                TextToSpeechManager.Event.Completed -> TtsPlaybackEvent.Completed
                is TextToSpeechManager.Event.Progress ->
                    TtsPlaybackEvent.Progress(current = event.current, total = event.total)
                is TextToSpeechManager.Event.Failed -> TtsPlaybackEvent.Failed
            }
        }

    override suspend fun play(article: TtsQueuePlayableArticle, startSegmentIndex: Int) {
        textToSpeechManager.readHtml(
            htmlContent = article.htmlContent,
            startSegmentIndex = startSegmentIndex,
        )
    }

    override fun stop() {
        textToSpeechManager.stop()
    }
}

fun ArticleWithFeed.toQueueItem(): TtsQueueItem =
    TtsQueueItem(
        articleId = article.id,
        title = article.title,
        feedName = feed.name,
        publishedAtMillis = article.date.time,
        imageUrl = article.img,
        htmlContent =
            resolvePlayableHtmlContent(
                rawDescription = article.rawDescription,
                shortDescription = article.shortDescription,
                title = article.title,
            ),
    )

fun ArticleWithFeed.toSummaryQueueItem(summaryHtmlContent: String, estimatedDurationMs: Long): TtsQueueItem =
    TtsQueueItem(
        articleId = article.id,
        title = article.title,
        feedName = feed.name,
        publishedAtMillis = article.date.time,
        imageUrl = article.img,
        contentType = TtsQueueContentType.AiSummary,
        summaryHtmlContent = summaryHtmlContent,
        estimatedDurationMs = estimatedDurationMs,
    )

fun buildSummaryHtmlContent(title: String, feedName: String, summary: String): String =
    listOf(title, feedName, summary)
        .filter { it.isNotBlank() }
        .joinToString(separator = "。")
        .let { "<p>${it.escapeHtml()}</p>" }

private fun String.escapeHtml(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

internal fun resolvePlayableHtmlContent(
    rawDescription: String,
    shortDescription: String,
    title: String,
): String? {
    val preferred =
        rawDescription.takeIf { it.isNotBlank() }
            ?: shortDescription.takeIf { it.isNotBlank() }?.let { "<p>$it</p>" }
            ?: title.takeIf { it.isNotBlank() }?.let { "<p>$it</p>" }
    return preferred?.takeIf { it.isNotBlank() }?.let(VideoNoiseCleaner::cleanHtml)
}
