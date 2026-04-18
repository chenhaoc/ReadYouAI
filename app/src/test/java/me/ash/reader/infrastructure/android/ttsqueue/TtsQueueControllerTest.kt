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
                    currentSegmentCount = 3,
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
                    "b" to playableArticle("b", segmentCharCounts = listOf(10)),
                    "c" to playableArticle("c", segmentCharCounts = listOf(10)),
                )
            )
        val playbackClient = FakePlaybackClient()
        val controller =
            TtsQueueController(
                snapshotStore = snapshotStore,
                articleRepository = repository,
                playbackClient = playbackClient,
                serviceLauncher = NoOpServiceLauncher,
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
                    "a" to playableArticle("a", segmentCharCounts = listOf(10)),
                    "b" to playableArticle("b", segmentCharCounts = listOf(10)),
                )
            )
        val playbackClient = FakePlaybackClient()
        val controller =
            TtsQueueController(
                snapshotStore = snapshotStore,
                articleRepository = repository,
                playbackClient = playbackClient,
                serviceLauncher = NoOpServiceLauncher,
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
                    "a" to playableArticle("a", html = "<p>db</p>", segmentCharCounts = listOf(10)),
                )
            )
        val playbackClient = FakePlaybackClient()
        val controller =
            TtsQueueController(
                snapshotStore = snapshotStore,
                articleRepository = repository,
                playbackClient = playbackClient,
                serviceLauncher = NoOpServiceLauncher,
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
                    currentSegmentCount = 4,
                )
            )
        val repository =
            FakeArticleRepository(
                mapOf(
                    "a" to playableArticle("a", html = "<p>db</p>", segmentCharCounts = listOf(10, 10, 10, 10)),
                )
            )
        val playbackClient = FakePlaybackClient()
        val controller =
            TtsQueueController(
                snapshotStore = snapshotStore,
                articleRepository = repository,
                playbackClient = playbackClient,
                serviceLauncher = NoOpServiceLauncher,
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
                    "a" to playableArticle("a", html = "<p>db</p>", segmentCharCounts = listOf(10, 10, 10, 10)),
                )
            )
        val playbackClient = FakePlaybackClient()
        val controller =
            TtsQueueController(
                snapshotStore = snapshotStore,
                articleRepository = repository,
                playbackClient = playbackClient,
                serviceLauncher = NoOpServiceLauncher,
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
    fun pause_keeps_current_article_and_service_running() = runTest {
        val snapshotStore = FakeSnapshotStore(null)
        val repository =
            FakeArticleRepository(
                mapOf(
                    "a" to playableArticle("a", segmentCharCounts = listOf(10, 10)),
                )
            )
        val playbackClient = FakePlaybackClient()
        val serviceLauncher = RecordingServiceLauncher()
        val controller =
            TtsQueueController(
                snapshotStore = snapshotStore,
                articleRepository = repository,
                playbackClient = playbackClient,
                serviceLauncher = serviceLauncher,
                coroutineScope = backgroundScope,
            )

        controller.playNow(playableArticle("a").item)
        advanceUntilIdle()
        controller.pause()
        advanceUntilIdle()

        assertEquals("a", controller.state.value.currentArticleId)
        assertEquals(TtsQueuePlaybackState.Idle, controller.state.value.playbackState)
        assertEquals(1, serviceLauncher.startCount)
        assertEquals(0, serviceLauncher.stopCount)
    }

    @Test
    fun seekCurrent_restarts_playback_from_requested_segment() = runTest {
        val snapshotStore = FakeSnapshotStore(null)
        val repository =
            FakeArticleRepository(
                mapOf(
                    "a" to playableArticle("a", html = "<p>db</p>", segmentCharCounts = listOf(10, 10, 10, 10, 10)),
                )
            )
        val playbackClient = FakePlaybackClient()
        val controller =
            TtsQueueController(
                snapshotStore = snapshotStore,
                articleRepository = repository,
                playbackClient = playbackClient,
                serviceLauncher = NoOpServiceLauncher,
                coroutineScope = backgroundScope,
            )

        controller.playNow(playableArticle("a").item)
        advanceUntilIdle()
        controller.handlePlaybackEvent(TtsPlaybackEvent.Progress(current = 2, total = 5))
        advanceUntilIdle()

        controller.seekCurrent(segmentIndex = 3)
        advanceUntilIdle()

        assertEquals(listOf(0, 3), playbackClient.playedStartSegmentIndices)
        assertEquals(3, controller.state.value.currentSegmentIndex)
        assertEquals(5, controller.state.value.currentSegmentCount)
    }

    @Test
    fun skipToPrevious_wraps_from_first_item_to_last_item() = runTest {
        val snapshotStore = FakeSnapshotStore(null)
        val repository =
            FakeArticleRepository(
                mapOf(
                    "a" to playableArticle("a", segmentCharCounts = listOf(10, 10)),
                    "b" to playableArticle("b", segmentCharCounts = listOf(10, 10)),
                )
            )
        val playbackClient = FakePlaybackClient()
        val controller =
            TtsQueueController(
                snapshotStore = snapshotStore,
                articleRepository = repository,
                playbackClient = playbackClient,
                serviceLauncher = NoOpServiceLauncher,
                coroutineScope = backgroundScope,
            )

        controller.playNow(playableArticle("a").item)
        controller.enqueue(playableArticle("b").item)
        advanceUntilIdle()

        controller.skipToPrevious()
        advanceUntilIdle()

        assertEquals("b", controller.state.value.currentArticleId)
        assertEquals(listOf("a", "b"), playbackClient.playedArticleIds)
    }

    @Test
    fun skipToNext_wraps_from_last_item_to_first_item() = runTest {
        val snapshotStore = FakeSnapshotStore(null)
        val repository =
            FakeArticleRepository(
                mapOf(
                    "a" to playableArticle("a", segmentCharCounts = listOf(10, 10)),
                    "b" to playableArticle("b", segmentCharCounts = listOf(10, 10)),
                )
            )
        val playbackClient = FakePlaybackClient()
        val controller =
            TtsQueueController(
                snapshotStore = snapshotStore,
                articleRepository = repository,
                playbackClient = playbackClient,
                serviceLauncher = NoOpServiceLauncher,
                coroutineScope = backgroundScope,
            )

        controller.playNow(playableArticle("a").item)
        controller.enqueue(playableArticle("b").item)
        advanceUntilIdle()
        controller.playNow(playableArticle("b").item)
        advanceUntilIdle()

        controller.skipToNext()
        advanceUntilIdle()

        assertEquals("a", controller.state.value.currentArticleId)
        assertEquals(listOf("a", "b", "a"), playbackClient.playedArticleIds)
    }

    @Test
    fun switching_articles_preserves_each_articles_bookmark() = runTest {
        val snapshotStore = FakeSnapshotStore(null)
        val repository =
            FakeArticleRepository(
                mapOf(
                    "a" to playableArticle("a", html = "<p>a1</p>\n<p>a2</p>\n<p>a3</p>", segmentCharCounts = listOf(10, 10, 10)),
                    "b" to playableArticle("b", html = "<p>b1</p>\n<p>b2</p>", segmentCharCounts = listOf(10, 10)),
                )
            )
        val playbackClient = FakePlaybackClient()
        val controller =
            TtsQueueController(
                snapshotStore = snapshotStore,
                articleRepository = repository,
                playbackClient = playbackClient,
                serviceLauncher = NoOpServiceLauncher,
                coroutineScope = backgroundScope,
            )

        controller.playNow(playableArticle("a").item)
        advanceUntilIdle()
        controller.handlePlaybackEvent(TtsPlaybackEvent.Progress(current = 2, total = 3))
        advanceUntilIdle()

        controller.playNow(playableArticle("b").item)
        advanceUntilIdle()
        controller.handlePlaybackEvent(TtsPlaybackEvent.Progress(current = 2, total = 2))
        advanceUntilIdle()

        controller.playNow(playableArticle("a").item)
        advanceUntilIdle()

        assertEquals(listOf("a", "b", "a"), playbackClient.playedArticleIds)
        assertEquals(listOf(0, 0, 1), playbackClient.playedStartSegmentIndices)
        assertEquals(listOf("a", "b"), controller.state.value.items.map(TtsQueueItem::articleId))
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

    private fun playableArticle(
        id: String,
        html: String = "<p>$id</p>",
        segmentCharCounts: List<Int> = listOf(10),
    ) =
        TtsQueuePlayableArticle(
            item =
                TtsQueueItem(
                    articleId = id,
                    title = "title-$id",
                    feedName = "feed-$id",
                ),
            htmlContent = html,
            segmentCharCounts = segmentCharCounts,
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

private object NoOpServiceLauncher : TtsPlaybackServiceLauncher {
    override fun startService() = Unit
    override fun stopService() = Unit
}

private class RecordingServiceLauncher : TtsPlaybackServiceLauncher {
    var startCount: Int = 0
    var stopCount: Int = 0

    override fun startService() {
        startCount += 1
    }

    override fun stopService() {
        stopCount += 1
    }
}
