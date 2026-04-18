package me.ash.reader.infrastructure.android.ttsqueue

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class TtsQueueSnapshot(
    val articleIds: List<String> = emptyList(),
    val currentArticleId: String? = null,
    val wasPlaying: Boolean = false,
    val currentProgress: Float? = null,
    val currentSegmentIndex: Int? = null,
    val currentSegmentCount: Int? = null,
    val bookmarks: List<TtsPlaybackBookmark> = emptyList(),
)

data class TtsQueuePlayableArticle(
    val item: TtsQueueItem,
    val htmlContent: String,
    val segmentCharCounts: List<Int> = emptyList(),
)

sealed interface TtsPlaybackEvent {
    data object Completed : TtsPlaybackEvent

    data class Progress(val current: Int, val total: Int) : TtsPlaybackEvent

    data object Failed : TtsPlaybackEvent
}

interface TtsQueueSnapshotStore {
    suspend fun readSnapshot(): TtsQueueSnapshot?

    suspend fun writeSnapshot(snapshot: TtsQueueSnapshot?)
}

interface TtsQueueArticleRepository {
    suspend fun getById(articleId: String): TtsQueuePlayableArticle?
}

interface TtsQueuePlaybackClient {
    val events: Flow<TtsPlaybackEvent>

    suspend fun play(article: TtsQueuePlayableArticle, startSegmentIndex: Int = 0)

    fun stop()
}

interface TtsPlaybackServiceLauncher {
    fun startService()
    fun stopService()
}

class TtsQueueController(
    private val snapshotStore: TtsQueueSnapshotStore,
    private val articleRepository: TtsQueueArticleRepository,
    private val playbackClient: TtsQueuePlaybackClient,
    private val serviceLauncher: TtsPlaybackServiceLauncher,
    private val coroutineScope: CoroutineScope,
) {
    private val _state = MutableStateFlow(TtsQueueState())
    val state = _state.asStateFlow()

    private var sleepTimerJob: Job? = null

    init {
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            playbackClient.events.collectLatest { event ->
                handlePlaybackEvent(event)
            }
        }
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            restore()
        }
    }

    fun enqueue(item: TtsQueueItem) {
        _state.value = TtsQueueReducer.append(_state.value, item)
        persistAsync()
    }

    fun playNow(item: TtsQueueItem) {
        _state.value =
            TtsQueueReducer.playNow(_state.value, item).copy(
                playbackState = TtsQueuePlaybackState.Preparing,
                currentSegmentStartedAtMillis = null,
                currentSegmentDurationMs = 0,
            ).syncSleepTimerTarget()
        persistAsync()
        serviceLauncher.startService()
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) { playCurrentArticle() }
    }

    fun resumeCurrent() {
        if (_state.value.currentArticleId == null) return
        _state.value = _state.value.copy(
            playbackState = TtsQueuePlaybackState.Preparing,
            currentSegmentStartedAtMillis = null,
            currentSegmentDurationMs = 0,
        )
        persistAsync()
        serviceLauncher.startService()
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) { playCurrentArticle() }
    }

    fun pause() {
        playbackClient.stop()
        _state.value = _state.value.copy(playbackState = TtsQueuePlaybackState.Idle)
        persistAsync()
    }

    fun skipToPrevious() {
        val items = _state.value.items
        if (items.isEmpty()) return
        val currentIndex = _state.value.currentIndex ?: return
        val previousIndex = if (currentIndex <= 0) items.lastIndex else currentIndex - 1
        playIndex(previousIndex)
    }

    fun skipToPreviousSegment() {
        if (!_state.value.hasPreviousSegment) return
        seekCurrent(_state.value.currentSegmentIndex - 1)
    }

    fun seekCurrent(segmentIndex: Int) {
        val articleId = _state.value.currentArticleId ?: return
        val safeSegmentIndex =
            if (_state.value.currentSegmentCount > 0) {
                segmentIndex.coerceIn(0, _state.value.currentSegmentCount - 1)
            } else {
                segmentIndex.coerceAtLeast(0)
            }
        playbackClient.stop()
        updateBookmark(articleId) { bookmark ->
            bookmark.copy(segmentIndex = safeSegmentIndex)
        }
        _state.value =
            _state.value.copy(
                playbackState = TtsQueuePlaybackState.Preparing,
                currentSegmentStartedAtMillis = null,
                currentSegmentDurationMs = 0,
            )
        persistAsync()
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) { playCurrentArticle() }
    }

    fun remove(articleId: String) {
        val wasCurrent = _state.value.currentArticleId == articleId
        _state.value = TtsQueueReducer.remove(_state.value, articleId).syncSleepTimerTarget()
        persistAsync()
        if (wasCurrent) {
            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                if (_state.value.currentArticleId == null) playbackClient.stop()
                else playCurrentArticle()
            }
        }
    }

    fun moveUp(articleId: String) {
        _state.value = TtsQueueReducer.moveUp(_state.value, articleId)
        persistAsync()
    }

    fun moveDown(articleId: String) {
        _state.value = TtsQueueReducer.moveDown(_state.value, articleId)
        persistAsync()
    }

    fun stop() {
        pause()
        clearSleepTimer()
        serviceLauncher.stopService()
    }

    fun skipToNext() {
        val items = _state.value.items
        if (items.isEmpty()) return
        val currentIndex = _state.value.currentIndex ?: return
        val nextIndex = if (currentIndex >= items.lastIndex) 0 else currentIndex + 1
        playIndex(nextIndex)
    }

    fun skipToNextSegment() {
        if (!_state.value.hasNextSegment) return
        seekCurrent(_state.value.currentSegmentIndex + 1)
    }

    fun setSleepTimer(option: TtsSleepTimerOption) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null

        _state.value =
            _state.value.copy(
                sleepTimer =
                    when (option) {
                        TtsSleepTimerOption.Off -> TtsSleepTimerState()
                        TtsSleepTimerOption.CurrentArticleEnd ->
                            TtsSleepTimerState(
                                option = option,
                                targetArticleId = _state.value.currentArticleId,
                            )
                        else ->
                            TtsSleepTimerState(
                                option = option,
                                endTimeMillis = System.currentTimeMillis() + (option.durationMs ?: 0L),
                            )
                    },
            )
        persistAsync()

        option.durationMs?.let { durationMs ->
            sleepTimerJob = coroutineScope.launch {
                delay(durationMs)
                sleepTimerJob = null
                stop()
            }
        }
    }

    fun clear() {
        playbackClient.stop()
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _state.value = TtsQueueReducer.clear(_state.value)
        persistAsync()
        serviceLauncher.stopService()
    }

    suspend fun restore() {
        val snapshot = snapshotStore.readSnapshot() ?: return
        val playableArticles =
            snapshot.articleIds.mapNotNull { articleId ->
                articleRepository.getById(articleId)
            }

        val items = playableArticles.map(TtsQueuePlayableArticle::item)
        val currentArticleId =
            snapshot.currentArticleId?.takeIf { currentId -> items.any { it.articleId == currentId } }
                ?: items.firstOrNull()?.articleId
        val legacyBookmark =
            snapshot.currentArticleId?.let { articleId ->
                if (snapshot.bookmarks.none { it.articleId == articleId }) {
                    TtsPlaybackBookmark(
                        articleId = articleId,
                        segmentIndex = snapshot.currentSegmentIndex ?: 0,
                        segmentCharCounts =
                            List(snapshot.currentSegmentCount ?: 0) { 1 },
                    )
                } else {
                    null
                }
            }
        val bookmarks =
            (snapshot.bookmarks + listOfNotNull(legacyBookmark))
                .filter { items.any { item -> item.articleId == it.articleId } }
                .associateBy(TtsPlaybackBookmark::articleId)

        _state.value =
            TtsQueueState(
                items = items,
                currentArticleId = currentArticleId,
                playbackState = TtsQueuePlaybackState.Idle,
                bookmarks = bookmarks,
            )
        persistAsync()

        if (snapshot.wasPlaying && currentArticleId != null) {
            serviceLauncher.startService()
            playCurrentArticle()
        }
    }

    suspend fun handlePlaybackEvent(event: TtsPlaybackEvent) {
        when (event) {
            TtsPlaybackEvent.Completed -> onPlaybackCompleted()
            is TtsPlaybackEvent.Progress ->
                currentArticleId()?.let { articleId ->
                    val segmentIndex = (event.current - 1).coerceAtLeast(0)
                    updateBookmark(articleId) { bookmark ->
                        bookmark.copy(segmentIndex = segmentIndex)
                    }
                    val segmentDurationMs = charsToMs(_state.value.currentSegmentCharCounts.getOrElse(segmentIndex) { 0 })
                    _state.value = _state.value.copy(
                        currentSegmentStartedAtMillis = System.currentTimeMillis(),
                        currentSegmentDurationMs = segmentDurationMs,
                    )
                }
            TtsPlaybackEvent.Failed ->
                _state.value =
                    _state.value.copy(
                        playbackState = TtsQueuePlaybackState.Error,
                        currentSegmentStartedAtMillis = null,
                        currentSegmentDurationMs = 0,
                    )
        }
        persistAsync()
    }

    private suspend fun onPlaybackCompleted() {
        val completedArticleId = _state.value.currentArticleId
        if (completedArticleId != null && shouldStopAfterCurrentArticle(completedArticleId)) {
            stop()
            return
        }

        val advanced =
            TtsQueueReducer.advance(_state.value).copy(
                playbackState = TtsQueuePlaybackState.Preparing,
            ).syncSleepTimerTarget()
        _state.value = advanced
        persistAsync()

        if (advanced.currentArticleId == null) {
            _state.value = advanced.copy(playbackState = TtsQueuePlaybackState.Idle)
            persistAsync()
            serviceLauncher.stopService()
            return
        }

        serviceLauncher.startService()
        playCurrentArticle()
    }

    private suspend fun playCurrentArticle() {
        val currentArticleId = _state.value.currentArticleId ?: return
        val playableArticle = articleRepository.getById(currentArticleId)
        if (playableArticle == null) {
            _state.value =
                TtsQueueReducer.remove(_state.value, currentArticleId).copy(
                    playbackState = TtsQueuePlaybackState.Error,
                    currentSegmentStartedAtMillis = null,
                    currentSegmentDurationMs = 0,
                ).syncSleepTimerTarget()
            persistAsync()
            return
        }

        val existingBookmark = _state.value.bookmarks[currentArticleId]
        val segmentCharCounts =
            when {
                playableArticle.segmentCharCounts.isNotEmpty() -> playableArticle.segmentCharCounts
                existingBookmark?.segmentCharCounts?.isNotEmpty() == true ->
                    existingBookmark.segmentCharCounts
                else -> listOf(1)
            }
        val safeSegmentIndex =
            existingBookmark?.segmentIndex?.coerceIn(
                minimumValue = 0,
                maximumValue = segmentCharCounts.lastIndex.coerceAtLeast(0),
            ) ?: 0
        updateBookmark(currentArticleId) {
            TtsPlaybackBookmark(
                articleId = currentArticleId,
                segmentIndex = safeSegmentIndex,
                segmentCharCounts = segmentCharCounts,
            )
        }

        playbackClient.play(
            article = playableArticle,
            startSegmentIndex = safeSegmentIndex,
        )
        _state.value =
            _state.value.copy(
                currentArticleId = playableArticle.item.articleId,
                playbackState = TtsQueuePlaybackState.Reading,
                currentSegmentStartedAtMillis = null,
                currentSegmentDurationMs = 0,
            )
        persistAsync()
    }

    private fun playIndex(index: Int) {
        val targetItem = _state.value.items.getOrNull(index) ?: return
        _state.value =
            _state.value.copy(
                currentArticleId = targetItem.articleId,
                playbackState = TtsQueuePlaybackState.Preparing,
                currentSegmentStartedAtMillis = null,
                currentSegmentDurationMs = 0,
            ).syncSleepTimerTarget()
        persistAsync()
        serviceLauncher.startService()
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) { playCurrentArticle() }
    }

    private fun clearSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        if (!_state.value.sleepTimer.enabled) return
        _state.value = _state.value.copy(sleepTimer = TtsSleepTimerState())
        persistAsync()
    }

    private fun shouldStopAfterCurrentArticle(articleId: String): Boolean {
        val sleepTimer = _state.value.sleepTimer
        return sleepTimer.option == TtsSleepTimerOption.CurrentArticleEnd &&
            sleepTimer.targetArticleId == articleId
    }

    private fun TtsQueueState.syncSleepTimerTarget(): TtsQueueState {
        if (sleepTimer.option != TtsSleepTimerOption.CurrentArticleEnd) return this
        return copy(sleepTimer = sleepTimer.copy(targetArticleId = currentArticleId))
    }

    private fun currentArticleId(): String? = _state.value.currentArticleId

    private fun updateBookmark(
        articleId: String,
        transform: (TtsPlaybackBookmark) -> TtsPlaybackBookmark,
    ) {
        val current = _state.value.bookmarks[articleId] ?: TtsPlaybackBookmark(articleId = articleId)
        _state.value =
            _state.value.copy(
                bookmarks = _state.value.bookmarks + (articleId to transform(current)),
            )
    }

    private fun persistAsync() {
        val snapshot =
            TtsQueueSnapshot(
                articleIds = _state.value.items.map(TtsQueueItem::articleId),
                currentArticleId = _state.value.currentArticleId,
                wasPlaying = _state.value.playbackState == TtsQueuePlaybackState.Reading,
                currentSegmentIndex = _state.value.currentSegmentIndex,
                currentSegmentCount = _state.value.currentSegmentCount,
                bookmarks = _state.value.bookmarks.values.toList(),
            )
        coroutineScope.launch { snapshotStore.writeSnapshot(snapshot) }
    }
}
