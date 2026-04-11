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
import me.ash.reader.infrastructure.android.TextToSpeechManager
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
) : TtsQueueArticleRepository {
    override suspend fun getById(articleId: String): TtsQueuePlayableArticle? {
        return articleDao.queryById(articleId)?.let { articleWithFeed ->
            TtsQueuePlayableArticle(
                item = articleWithFeed.toQueueItem(),
                htmlContent =
                    resolvePlayableHtmlContent(
                        rawDescription = articleWithFeed.article.rawDescription,
                        shortDescription = articleWithFeed.article.shortDescription,
                        title = articleWithFeed.article.title,
                    ) ?: return null,
            )
        }
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
                is TextToSpeechManager.Event.Failed -> TtsPlaybackEvent.Failed
            }
        }

    override suspend fun play(article: TtsQueuePlayableArticle) {
        textToSpeechManager.readHtml(article.htmlContent)
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
        imageUrl = article.img,
        htmlContent =
            resolvePlayableHtmlContent(
                rawDescription = article.rawDescription,
                shortDescription = article.shortDescription,
                title = article.title,
            ),
    )

internal fun resolvePlayableHtmlContent(
    rawDescription: String,
    shortDescription: String,
    title: String,
): String? {
    val preferred =
        rawDescription.takeIf { it.isNotBlank() }
            ?: shortDescription.takeIf { it.isNotBlank() }?.let { "<p>$it</p>" }
            ?: title.takeIf { it.isNotBlank() }?.let { "<p>$it</p>" }
    return preferred?.takeIf { it.isNotBlank() }
}
