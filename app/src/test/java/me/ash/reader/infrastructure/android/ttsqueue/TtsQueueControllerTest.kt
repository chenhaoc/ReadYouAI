package me.ash.reader.infrastructure.android.ttsqueue

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TtsQueueControllerTest {

    @Test
    fun queueSnapshot_is_serializable() {
        val encoded =
            Json.encodeToString(
                TtsQueueSnapshot(
                    articleIds = listOf("a", "b"),
                    currentArticleId = "a",
                    wasPlaying = true,
                    currentSegmentIndex = 1,
                )
            )

        assertTrue(encoded.contains("\"articleIds\""))
        assertTrue(encoded.contains("\"currentArticleId\":\"a\""))
    }

    @Test
    fun restore_filters_missing_articles_and_keeps_first_surviving_current() = runTest {
        val snapshotStore =
            FakeSnapshotStore(
                TtsQueueSnapshot(
                    articleIds = listOf("missing", "b", "c"),
                    currentArticleId = "missing",
                    wasPlaying = false,
                )
            )
        val repository =
            FakeArticleRepository(
                mapOf(
                    "b" to playableArticle("b"),
                    "c" to playableArticle("c"),
                )
            )
        val playbackClient = FakePlaybackClient()
        val controller =
            TtsQueueController(
                snapshotStore = snapshotStore,
                articleRepository = repository,
                playbackClient = playbackClient,
                coroutineScope = backgroundScope,
            )

        advanceUntilIdle()

        assertEquals(listOf("b", "c"), controller.state.value.items.map(TtsQueueItem::articleId))
        assertEquals("b", controller.state.value.currentArticleId)
        assertEquals(emptyList<String>(), playbackClient.playedArticleIds)
    }

    @Test
    fun playback_completion_advances_to_next_article_and_starts_it() = runTest {
        val snapshotStore = FakeSnapshotStore(null)
        val repository =
            FakeArticleRepository(
                mapOf(
                    "a" to playableArticle("a"),
                    "b" to playableArticle("b"),
                )
            )
        val playbackClient = FakePlaybackClient()
        val controller =
            TtsQueueController(
                snapshotStore = snapshotStore,
                articleRepository = repository,
                playbackClient = playbackClient,
                coroutineScope = backgroundScope,
            )

        controller.playNow(playableArticle("a").item)
        controller.enqueue(playableArticle("b").item)
        advanceUntilIdle()

        controller.handlePlaybackEvent(TtsPlaybackEvent.Completed)
        advanceUntilIdle()

        assertEquals("b", controller.state.value.currentArticleId)
        assertEquals(listOf("a", "b"), playbackClient.playedArticleIds)
    }

    @Test
    fun playNow_uses_repository_content_for_existing_article() = runTest {
        val snapshotStore = FakeSnapshotStore(null)
        val repository =
            FakeArticleRepository(
                mapOf(
                    "a" to playableArticle("a", html = "<p>db</p>"),
                )
            )
        val playbackClient = FakePlaybackClient()
        val controller =
            TtsQueueController(
                snapshotStore = snapshotStore,
                articleRepository = repository,
                playbackClient = playbackClient,
                coroutineScope = backgroundScope,
            )

        controller.playNow(
            TtsQueueItem(
                articleId = "a",
                title = "title-a",
                feedName = "feed-a",
                htmlContent = "<p>inline</p>",
            )
        )
        advanceUntilIdle()

        assertEquals(listOf("<p>db</p>"), playbackClient.playedHtmlContents)
    }

    @Test
    fun restore_resumes_playback_when_snapshot_was_playing() = runTest {
        val snapshotStore =
            FakeSnapshotStore(
                TtsQueueSnapshot(
                    articleIds = listOf("a"),
                    currentArticleId = "a",
                    wasPlaying = true,
                    currentSegmentIndex = 2,
                )
            )
        val repository =
            FakeArticleRepository(
                mapOf(
                    "a" to playableArticle("a", html = "<p>db</p>"),
                )
            )
        val playbackClient = FakePlaybackClient()
        val controller =
            TtsQueueController(
                snapshotStore = snapshotStore,
                articleRepository = repository,
                playbackClient = playbackClient,
                coroutineScope = backgroundScope,
            )

        advanceUntilIdle()

        assertEquals(listOf("a"), playbackClient.playedArticleIds)
        assertEquals(listOf(2), playbackClient.playedStartSegmentIndices)
        assertEquals(TtsQueuePlaybackState.Reading, controller.state.value.playbackState)
    }

    @Test
    fun progress_event_is_persisted_and_resume_continues_from_last_segment() = runTest {
        val snapshotStore = FakeSnapshotStore(null)
        val repository =
            FakeArticleRepository(
                mapOf(
                    "a" to playableArticle("a", html = "<p>db</p>"),
                )
            )
        val playbackClient = FakePlaybackClient()
        val controller =
            TtsQueueController(
                snapshotStore = snapshotStore,
                articleRepository = repository,
                playbackClient = playbackClient,
                coroutineScope = backgroundScope,
            )

        controller.playNow(playableArticle("a").item)
        advanceUntilIdle()

        controller.handlePlaybackEvent(TtsPlaybackEvent.Progress(current = 2, total = 4))
        advanceUntilIdle()
        controller.stop()
        advanceUntilIdle()
        controller.resumeCurrent()
        advanceUntilIdle()

        assertEquals(listOf(0, 1), playbackClient.playedStartSegmentIndices)
        assertEquals(1, controller.state.value.currentSegmentIndex)
    }

    @Test
    fun resolvePlayableHtmlContent_falls_back_to_short_description_then_title() {
        assertEquals(
            "<p>short</p>",
            resolvePlayableHtmlContent(
                rawDescription = "",
                shortDescription = "short",
                title = "title",
            ),
        )
        assertEquals(
            "<p>title</p>",
            resolvePlayableHtmlContent(
                rawDescription = "",
                shortDescription = "",
                title = "title",
            ),
        )
    }

    private fun playableArticle(id: String, html: String = "<p>$id</p>") =
        TtsQueuePlayableArticle(
            item =
                TtsQueueItem(
                    articleId = id,
                    title = "title-$id",
                    feedName = "feed-$id",
                ),
            htmlContent = html,
        )
}

private class FakeSnapshotStore(
    initialSnapshot: TtsQueueSnapshot?,
) : TtsQueueSnapshotStore {
    private var snapshotValue: TtsQueueSnapshot? = initialSnapshot

    override suspend fun readSnapshot(): TtsQueueSnapshot? = snapshotValue

    override suspend fun writeSnapshot(snapshot: TtsQueueSnapshot?) {
        snapshotValue = snapshot
    }
}

private class FakeArticleRepository(
    private val articles: Map<String, TtsQueuePlayableArticle>,
) : TtsQueueArticleRepository {
    override suspend fun getById(articleId: String): TtsQueuePlayableArticle? = articles[articleId]
}

private class FakePlaybackClient : TtsQueuePlaybackClient {
    private val _events = MutableSharedFlow<TtsPlaybackEvent>(replay = 1, extraBufferCapacity = 1)
    val playedArticleIds = mutableListOf<String>()
    val playedHtmlContents = mutableListOf<String>()
    val playedStartSegmentIndices = mutableListOf<Int>()

    override val events: Flow<TtsPlaybackEvent> = _events

    override suspend fun play(article: TtsQueuePlayableArticle, startSegmentIndex: Int) {
        playedArticleIds += article.item.articleId
        playedHtmlContents += article.htmlContent
        playedStartSegmentIndices += startSegmentIndex
    }

    override fun stop() = Unit
}
