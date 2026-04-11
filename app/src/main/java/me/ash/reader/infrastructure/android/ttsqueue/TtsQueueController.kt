package me.ash.reader.infrastructure.android.ttsqueue

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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
)

data class TtsQueuePlayableArticle(
    val item: TtsQueueItem,
    val htmlContent: String,
)

sealed interface TtsPlaybackEvent {
    data object Completed : TtsPlaybackEvent

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

    suspend fun play(article: TtsQueuePlayableArticle)

    fun stop()
}

class TtsQueueController(
    private val snapshotStore: TtsQueueSnapshotStore,
    private val articleRepository: TtsQueueArticleRepository,
    private val playbackClient: TtsQueuePlaybackClient,
    private val coroutineScope: CoroutineScope,
) {
    private val _state = MutableStateFlow(TtsQueueState())
    val state = _state.asStateFlow()

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
                playbackState = TtsQueuePlaybackState.Preparing
            )
        persistAsync()
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) { playCurrentArticle() }
    }

    fun remove(articleId: String) {
        val wasCurrent = _state.value.currentArticleId == articleId
        _state.value = TtsQueueReducer.remove(_state.value, articleId)
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
        playbackClient.stop()
        _state.value = _state.value.copy(playbackState = TtsQueuePlaybackState.Idle)
        persistAsync()
    }

    fun skipToNext() {
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            handlePlaybackEvent(TtsPlaybackEvent.Completed)
        }
    }

    fun clear() {
        playbackClient.stop()
        _state.value = TtsQueueReducer.clear(_state.value)
        persistAsync()
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

        _state.value =
            TtsQueueState(
                items = items,
                currentArticleId = currentArticleId,
                playbackState = TtsQueuePlaybackState.Idle,
            )
        persistAsync()

        if (snapshot.wasPlaying && currentArticleId != null) {
            playCurrentArticle()
        }
    }

    suspend fun handlePlaybackEvent(event: TtsPlaybackEvent) {
        when (event) {
            TtsPlaybackEvent.Completed -> onPlaybackCompleted()
            TtsPlaybackEvent.Failed ->
                _state.value =
                    _state.value.copy(playbackState = TtsQueuePlaybackState.Error)
        }
        persistAsync()
    }

    private suspend fun onPlaybackCompleted() {
        val advanced =
            TtsQueueReducer.advance(_state.value).copy(
                playbackState = TtsQueuePlaybackState.Preparing
            )
        _state.value = advanced
        persistAsync()

        if (advanced.currentArticleId == null) {
            _state.value = advanced.copy(playbackState = TtsQueuePlaybackState.Idle)
            persistAsync()
            return
        }

        playCurrentArticle()
    }

    private suspend fun playCurrentArticle() {
        val currentArticleId = _state.value.currentArticleId ?: return
        val playableArticle = articleRepository.getById(currentArticleId)
        if (playableArticle == null) {
            _state.value =
                TtsQueueReducer.remove(_state.value, currentArticleId).copy(
                    playbackState = TtsQueuePlaybackState.Error
                )
            persistAsync()
            return
        }

        playbackClient.play(playableArticle)
        _state.value =
            _state.value.copy(
                currentArticleId = playableArticle.item.articleId,
                playbackState = TtsQueuePlaybackState.Reading,
            )
        persistAsync()
    }

    private fun persistAsync() {
        val snapshot =
            TtsQueueSnapshot(
                articleIds = _state.value.items.map(TtsQueueItem::articleId),
                currentArticleId = _state.value.currentArticleId,
                wasPlaying = _state.value.playbackState == TtsQueuePlaybackState.Reading,
            )
        coroutineScope.launch { snapshotStore.writeSnapshot(snapshot) }
    }
}
