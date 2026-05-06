package me.ash.reader.infrastructure.android.ttsqueue

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    val mode: TtsQueueMode = TtsQueueMode.Normal,
    val normalItems: List<TtsQueueItem> = emptyList(),
    val normalCurrentArticleId: String? = null,
    val normalBookmarks: List<TtsPlaybackBookmark> = emptyList(),
    val commuteItems: List<TtsQueueItem> = emptyList(),
    val commuteCurrentArticleId: String? = null,
    val commuteBookmarks: List<TtsPlaybackBookmark> = emptyList(),
    val commuteMeta: TtsCommuteQueueMeta? = null,
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
    suspend fun get(item: TtsQueueItem): TtsQueuePlayableArticle?

    suspend fun getById(articleId: String): TtsQueuePlayableArticle?

    suspend fun isUnread(articleId: String): Boolean

    suspend fun markAsRead(articleId: String)

    fun observeIsStarred(articleId: String): Flow<Boolean>

    suspend fun markAsStarred(articleId: String, isStarred: Boolean)
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
    private val markReadOnCommuteComplete: () -> Boolean = { false },
) {
    private var normalState = TtsQueueState(mode = TtsQueueMode.Normal)
    private var commuteState = TtsQueueState(mode = TtsQueueMode.Commute)
    private val _state = MutableStateFlow(normalState)
    val state = _state.asStateFlow()
    private val restoreCompleted = CompletableDeferred<Unit>()

    private var sleepTimerJob: Job? = null

    init {
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            playbackClient.events.collectLatest { event ->
                handlePlaybackEvent(event)
            }
        }
        observeCurrentItemStarred()
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                restore()
            } finally {
                if (!restoreCompleted.isCompleted) {
                    restoreCompleted.complete(Unit)
                }
            }
        }
    }

    val isRestoreCompleted: Boolean
        get() = restoreCompleted.isCompleted

    suspend fun awaitRestore() {
        restoreCompleted.await()
    }

    fun switchMode(mode: TtsQueueMode) {
        if (_state.value.mode == mode) return
        playbackClient.stop()
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        setActiveState(
            _state.value.copy(
                playbackState = TtsQueuePlaybackState.Idle,
                sleepTimer = TtsSleepTimerState(),
                currentSegmentStartedAtMillis = null,
                currentSegmentDurationMs = 0,
            )
        )
        _state.value = stateFor(mode).copy(
            mode = mode,
            playbackState = TtsQueuePlaybackState.Idle,
            sleepTimer = TtsSleepTimerState(),
            currentSegmentStartedAtMillis = null,
            currentSegmentDurationMs = 0,
        )
        setActiveState(_state.value)
        persistAsync(wasPlaying = false)
        serviceLauncher.stopService()
    }

    fun enqueue(item: TtsQueueItem, mode: TtsQueueMode = TtsQueueMode.Normal) {
        updateQueue(mode) { TtsQueueReducer.append(it, item) }
        persistAsync()
    }

    fun appendCommuteQueue(items: List<TtsQueueItem>) {
        if (items.isEmpty()) return
        updateQueue(TtsQueueMode.Commute) { state ->
            val updated =
                items.fold(state.copy(commuteMeta = null)) { acc, item ->
                    TtsQueueReducer.append(acc, item)
                }
            if (updated.currentArticleId == null) {
                updated.copy(currentArticleId = updated.items.firstOrNull()?.articleId)
            } else {
                updated
            }
        }
        persistAsync()
    }

    fun replaceCommuteQueue(items: List<TtsQueueItem>, meta: TtsCommuteQueueMeta?) {
        playbackClient.stop()
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        val updated =
            TtsQueueState(
                items = items,
                currentArticleId = items.firstOrNull()?.articleId,
                mode = TtsQueueMode.Commute,
                commuteMeta = meta,
            )
        commuteState = updated
        if (_state.value.mode == TtsQueueMode.Commute) {
            _state.value = updated
        }
        persistAsync(wasPlaying = false)
        serviceLauncher.stopService()
    }

    fun playNow(item: TtsQueueItem, mode: TtsQueueMode = TtsQueueMode.Normal) {
        playbackClient.stop()
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        val previousMode = _state.value.mode
        if (previousMode != mode) {
            setActiveState(
                _state.value.copy(
                    playbackState = TtsQueuePlaybackState.Idle,
                    sleepTimer = TtsSleepTimerState(),
                    currentSegmentStartedAtMillis = null,
                    currentSegmentDurationMs = 0,
                )
            )
        }
        updateQueue(mode) {
            TtsQueueReducer.playNow(it, item).copy(
                playbackState = TtsQueuePlaybackState.Preparing,
                currentSegmentStartedAtMillis = null,
                currentSegmentDurationMs = 0,
            ).syncSleepTimerTarget()
        }
        if (previousMode != mode) {
            _state.value = stateFor(mode)
        }
        persistAsync()
        serviceLauncher.startService()
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) { playCurrentArticle() }
    }

    fun resumeCurrent() {
        if (_state.value.currentArticleId == null) return
        startCurrentPlayback()
    }

    fun pause() {
        playbackClient.stop()
        setActiveState(_state.value.copy(playbackState = TtsQueuePlaybackState.Idle))
        persistAsync(wasPlaying = false)
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
        setActiveState(
            _state.value.copy(
                playbackState = TtsQueuePlaybackState.Preparing,
                currentSegmentStartedAtMillis = null,
                currentSegmentDurationMs = 0,
            )
        )
        persistAsync()
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) { playCurrentArticle() }
    }

    fun remove(articleId: String) {
        val wasCurrent = _state.value.currentArticleId == articleId
        setActiveState(TtsQueueReducer.remove(_state.value, articleId).syncSleepTimerTarget())
        persistAsync()
        if (wasCurrent) {
            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                if (_state.value.currentArticleId == null) playbackClient.stop()
                else playCurrentArticle()
            }
        }
    }

    fun moveUp(articleId: String) {
        setActiveState(TtsQueueReducer.moveUp(_state.value, articleId))
        persistAsync()
    }

    fun moveDown(articleId: String) {
        setActiveState(TtsQueueReducer.moveDown(_state.value, articleId))
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

        setActiveState(
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

    fun toggleCurrentStarred() {
        val articleId = _state.value.currentArticleId ?: return
        val targetStarred = !_state.value.currentItemStarred
        setCurrentItemStarred(targetStarred)
        coroutineScope.launch {
            runCatching {
                articleRepository.markAsStarred(articleId = articleId, isStarred = targetStarred)
            }
        }
    }

    fun clear() {
        playbackClient.stop()
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        setActiveState(TtsQueueReducer.clear(_state.value))
        persistAsync(wasPlaying = false)
        serviceLauncher.stopService()
    }

    suspend fun restore() {
        val snapshot = snapshotStore.readSnapshot() ?: return
        normalState = restoreNormalState(snapshot)
        commuteState = restoreCommuteState(snapshot)
        _state.value = stateFor(snapshot.mode).copy(playbackState = TtsQueuePlaybackState.Idle)
        persistAsync(wasPlaying = false)

        if (snapshot.wasPlaying && _state.value.currentArticleId != null) {
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
                    setActiveState(
                        _state.value.copy(
                            currentSegmentStartedAtMillis = System.currentTimeMillis(),
                            currentSegmentDurationMs = segmentDurationMs,
                        )
                    )
                }
            TtsPlaybackEvent.Failed ->
                setActiveState(
                    _state.value.copy(
                        playbackState = TtsQueuePlaybackState.Error,
                        currentSegmentStartedAtMillis = null,
                        currentSegmentDurationMs = 0,
                    )
                )
        }
        persistAsync()
    }

    private suspend fun restoreNormalState(snapshot: TtsQueueSnapshot): TtsQueueState {
        val playableArticles =
            if (snapshot.normalItems.isNotEmpty()) {
                snapshot.normalItems.mapNotNull { articleRepository.get(it) }
            } else {
                snapshot.articleIds.mapNotNull { articleRepository.getById(it) }
            }
        val items = playableArticles.map(TtsQueuePlayableArticle::item)
        val currentArticleId =
            (snapshot.normalCurrentArticleId ?: snapshot.currentArticleId)
                ?.takeIf { currentId -> items.any { it.articleId == currentId } }
                ?: items.firstOrNull()?.articleId
        val legacyBookmark =
            snapshot.currentArticleId?.let { articleId ->
                if (snapshot.bookmarks.none { it.articleId == articleId }) {
                    TtsPlaybackBookmark(
                        articleId = articleId,
                        segmentIndex = snapshot.currentSegmentIndex ?: 0,
                        segmentCharCounts = List(snapshot.currentSegmentCount ?: 0) { 1 },
                    )
                } else {
                    null
                }
            }
        val bookmarks =
            (snapshot.normalBookmarks.ifEmpty { snapshot.bookmarks } + listOfNotNull(legacyBookmark))
                .filter { items.any { item -> item.articleId == it.articleId } }
                .associateBy(TtsPlaybackBookmark::articleId)
        return TtsQueueState(
            items = items,
            currentArticleId = currentArticleId,
            bookmarks = bookmarks,
            mode = TtsQueueMode.Normal,
        )
    }

    private suspend fun restoreCommuteState(snapshot: TtsQueueSnapshot): TtsQueueState {
        val playableArticles = snapshot.commuteItems.mapNotNull { articleRepository.get(it) }
        val items = playableArticles.map(TtsQueuePlayableArticle::item)
        val currentArticleId =
            snapshot.commuteCurrentArticleId?.takeIf { currentId -> items.any { it.articleId == currentId } }
                ?: items.firstOrNull()?.articleId
        val bookmarks =
            snapshot.commuteBookmarks
                .filter { items.any { item -> item.articleId == it.articleId } }
                .associateBy(TtsPlaybackBookmark::articleId)
        return TtsQueueState(
            items = items,
            currentArticleId = currentArticleId,
            bookmarks = bookmarks,
            mode = TtsQueueMode.Commute,
            commuteMeta = snapshot.commuteMeta,
        )
    }

    private suspend fun onPlaybackCompleted() {
        val completedItem = _state.value.currentItem
        val completedArticleId = completedItem?.articleId
        if (completedArticleId != null && shouldStopAfterCurrentArticle(completedArticleId)) {
            stop()
            return
        }

        if (completedArticleId != null) {
            resetBookmarkToBeginning(completedArticleId)
            scheduleAutoMarkAsReadOnCompletion(
                completedItem = completedItem,
                completedArticleId = completedArticleId,
            )
        }

        val advanced =
            TtsQueueReducer.advance(_state.value).copy(
                playbackState = TtsQueuePlaybackState.Preparing,
            ).syncSleepTimerTarget()
        setActiveState(advanced)
        persistAsync()

        if (advanced.currentArticleId == null) {
            setActiveState(advanced.copy(playbackState = TtsQueuePlaybackState.Idle))
            persistAsync(wasPlaying = false)
            serviceLauncher.stopService()
            return
        }

        serviceLauncher.startService()
        playCurrentArticle()
    }

    private fun shouldAutoMarkAsReadOnCompletion(completedItem: TtsQueueItem?): Boolean =
        when (_state.value.mode) {
            TtsQueueMode.Commute -> markReadOnCommuteComplete()
            TtsQueueMode.Normal -> completedItem?.contentType == TtsQueueContentType.FullArticle
        }

    private suspend fun playCurrentArticle() {
        val currentItem = _state.value.currentItem ?: return
        val playableArticle = articleRepository.get(currentItem)
        if (playableArticle == null) {
            setActiveState(
                TtsQueueReducer.remove(_state.value, currentItem.articleId).copy(
                    playbackState = TtsQueuePlaybackState.Error,
                    currentSegmentStartedAtMillis = null,
                    currentSegmentDurationMs = 0,
                ).syncSleepTimerTarget()
            )
            persistAsync()
            return
        }

        val currentArticleId = playableArticle.item.articleId
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
        setActiveState(
            _state.value.copy(
                currentArticleId = currentArticleId,
                playbackState = TtsQueuePlaybackState.Reading,
                currentSegmentStartedAtMillis = null,
                currentSegmentDurationMs = 0,
            )
        )
        persistAsync()
    }

    private fun playIndex(index: Int) {
        val targetItem = _state.value.items.getOrNull(index) ?: return
        setActiveState(
            _state.value.copy(
                currentArticleId = targetItem.articleId,
                playbackState = TtsQueuePlaybackState.Preparing,
                currentSegmentStartedAtMillis = null,
                currentSegmentDurationMs = 0,
            ).syncSleepTimerTarget()
        )
        persistAsync()
        serviceLauncher.startService()
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) { playCurrentArticle() }
    }

    private fun startCurrentPlayback() {
        setActiveState(
            _state.value.copy(
                playbackState = TtsQueuePlaybackState.Preparing,
                currentSegmentStartedAtMillis = null,
                currentSegmentDurationMs = 0,
            )
        )
        persistAsync()
        serviceLauncher.startService()
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) { playCurrentArticle() }
    }

    private fun resetBookmarkToBeginning(articleId: String) {
        updateBookmark(articleId) { bookmark ->
            bookmark.copy(segmentIndex = 0)
        }
    }

    private fun scheduleAutoMarkAsReadOnCompletion(
        completedItem: TtsQueueItem?,
        completedArticleId: String,
    ) {
        if (!shouldAutoMarkAsReadOnCompletion(completedItem)) return
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (articleRepository.isUnread(completedArticleId)) {
                articleRepository.markAsRead(completedArticleId)
            }
        }
    }

    private fun clearSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        if (!_state.value.sleepTimer.enabled) return
        setActiveState(_state.value.copy(sleepTimer = TtsSleepTimerState()))
        persistAsync(wasPlaying = false)
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
        setActiveState(
            _state.value.copy(
                bookmarks = _state.value.bookmarks + (articleId to transform(current)),
            )
        )
    }

    private fun observeCurrentItemStarred() {
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            _state
                .map { it.mode to it.currentArticleId }
                .distinctUntilChanged()
                .collectLatest { (_, articleId) ->
                    if (articleId == null) {
                        setCurrentItemStarred(false)
                    } else {
                        articleRepository.observeIsStarred(articleId).collectLatest { isStarred ->
                            setCurrentItemStarred(isStarred)
                        }
                    }
                }
        }
    }

    private fun setCurrentItemStarred(isStarred: Boolean) {
        if (_state.value.currentItemStarred == isStarred) return
        setActiveState(_state.value.copy(currentItemStarred = isStarred))
    }

    private fun updateQueue(
        mode: TtsQueueMode,
        transform: (TtsQueueState) -> TtsQueueState,
    ) {
        val updated = transform(stateFor(mode)).copy(mode = mode)
        if (mode == TtsQueueMode.Normal) {
            normalState = updated
        } else {
            commuteState = updated
        }
        if (_state.value.mode == mode) {
            _state.value = updated
        }
    }

    private fun setActiveState(state: TtsQueueState) {
        val normalized = state.copy(mode = _state.value.mode)
        _state.value = normalized
        if (normalized.mode == TtsQueueMode.Normal) {
            normalState = normalized
        } else {
            commuteState = normalized
        }
    }

    private fun stateFor(mode: TtsQueueMode): TtsQueueState =
        if (mode == TtsQueueMode.Normal) normalState else commuteState

    private fun persistAsync(wasPlaying: Boolean = _state.value.playbackState == TtsQueuePlaybackState.Reading) {
        val snapshot =
            TtsQueueSnapshot(
                articleIds = normalState.items.map(TtsQueueItem::articleId),
                currentArticleId = normalState.currentArticleId,
                wasPlaying = wasPlaying,
                currentSegmentIndex = normalState.currentSegmentIndex,
                currentSegmentCount = normalState.currentSegmentCount,
                bookmarks = normalState.bookmarks.values.toList(),
                mode = _state.value.mode,
                normalItems = normalState.items,
                normalCurrentArticleId = normalState.currentArticleId,
                normalBookmarks = normalState.bookmarks.values.toList(),
                commuteItems = commuteState.items,
                commuteCurrentArticleId = commuteState.currentArticleId,
                commuteBookmarks = commuteState.bookmarks.values.toList(),
                commuteMeta = commuteState.commuteMeta,
            )
        coroutineScope.launch { snapshotStore.writeSnapshot(snapshot) }
    }
}
