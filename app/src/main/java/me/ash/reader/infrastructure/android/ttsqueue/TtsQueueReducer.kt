package me.ash.reader.infrastructure.android.ttsqueue

import kotlinx.serialization.Serializable

private const val FIVE_MINUTES_MS = 5 * 60 * 1000L
private const val TEN_MINUTES_MS = 10 * 60 * 1000L
private const val FIFTEEN_MINUTES_MS = 15 * 60 * 1000L
private const val THIRTY_MINUTES_MS = 30 * 60 * 1000L

@Serializable
data class TtsQueueItem(
    val articleId: String,
    val title: String,
    val feedName: String,
    val publishedAtMillis: Long? = null,
    val imageUrl: String? = null,
    val htmlContent: String? = null,
    val contentType: TtsQueueContentType = TtsQueueContentType.FullArticle,
    val summaryHtmlContent: String? = null,
    val estimatedDurationMs: Long? = null,
)

enum class TtsQueueMode {
    Normal,
    Commute,
}

enum class TtsQueueContentType {
    FullArticle,
    AiSummary,
}

enum class TtsCommuteQueueGenerationMode {
    NewestFirst,
    AiRecommended,
}

@Serializable
data class TtsCommuteQueueMeta(
    val generatedAtMillis: Long,
    val targetDurationMinutes: Int,
    val estimatedDurationMinutes: Int,
    val itemCount: Int,
    val generationMode: TtsCommuteQueueGenerationMode = TtsCommuteQueueGenerationMode.NewestFirst,
)

enum class TtsQueuePlaybackState {
    Idle,
    Preparing,
    Reading,
    Error,
}

enum class TtsSleepTimerOption(val durationMs: Long? = null) {
    Off,
    FiveMinutes(durationMs = FIVE_MINUTES_MS),
    TenMinutes(durationMs = TEN_MINUTES_MS),
    FifteenMinutes(durationMs = FIFTEEN_MINUTES_MS),
    ThirtyMinutes(durationMs = THIRTY_MINUTES_MS),
    CurrentArticleEnd,
}

data class TtsSleepTimerState(
    val option: TtsSleepTimerOption = TtsSleepTimerOption.Off,
    val endTimeMillis: Long? = null,
    val targetArticleId: String? = null,
) {
    val enabled: Boolean
        get() = option != TtsSleepTimerOption.Off
}

@Serializable
data class TtsPlaybackBookmark(
    val articleId: String,
    val segmentIndex: Int = 0,
    val segmentCharCounts: List<Int> = emptyList(),
) {
    val segmentCount: Int
        get() = segmentCharCounts.size
}

data class TtsQueueState(
    val items: List<TtsQueueItem> = emptyList(),
    val currentArticleId: String? = null,
    val currentItemStarred: Boolean = false,
    val playbackState: TtsQueuePlaybackState = TtsQueuePlaybackState.Idle,
    val bookmarks: Map<String, TtsPlaybackBookmark> = emptyMap(),
    val sleepTimer: TtsSleepTimerState = TtsSleepTimerState(),
    val currentSegmentStartedAtMillis: Long? = null,
    val currentSegmentDurationMs: Long = 0,
    val mode: TtsQueueMode = TtsQueueMode.Normal,
    val commuteMeta: TtsCommuteQueueMeta? = null,
) {
    val currentIndex: Int?
        get() = items.indexOfFirst { it.articleId == currentArticleId }.takeIf { it >= 0 }

    val currentItem: TtsQueueItem?
        get() = currentIndex?.let(items::getOrNull)

    val currentBookmark: TtsPlaybackBookmark?
        get() = currentArticleId?.let(bookmarks::get)

    val currentSegmentIndex: Int
        get() = currentBookmark?.segmentIndex ?: 0

    val currentSegmentCount: Int
        get() = currentBookmark?.segmentCount ?: 0

    val currentSegmentCharCounts: List<Int>
        get() = currentBookmark?.segmentCharCounts ?: emptyList()

    val hasPreviousSegment: Boolean
        get() = currentSegmentIndex > 0

    val hasNextSegment: Boolean
        get() = currentSegmentCount > 0 && currentSegmentIndex < currentSegmentCount - 1
}

object TtsQueueReducer {

    fun append(state: TtsQueueState, item: TtsQueueItem): TtsQueueState {
        if (state.items.any { it.articleId == item.articleId }) return state
        return state.copy(items = state.items + item)
    }

    fun playNow(state: TtsQueueState, item: TtsQueueItem): TtsQueueState {
        val items =
            if (state.items.any { it.articleId == item.articleId }) {
                state.items
            } else {
                state.items + item
            }
        return state.copy(
            items = items,
            currentArticleId = item.articleId,
        )
    }

    fun advance(state: TtsQueueState): TtsQueueState {
        val currentIndex = state.currentIndex ?: return state
        val nextItem = state.items.getOrNull(currentIndex + 1)
        return state.copy(
            currentArticleId = nextItem?.articleId,
            playbackState = if (nextItem == null) TtsQueuePlaybackState.Idle else state.playbackState,
            currentSegmentStartedAtMillis = null,
            currentSegmentDurationMs = 0,
        )
    }

    fun remove(state: TtsQueueState, articleId: String): TtsQueueState {
        val currentIndex = state.currentIndex
        val removedIndex = state.items.indexOfFirst { it.articleId == articleId }
        if (removedIndex == -1) return state

        val updatedItems = state.items.filterNot { it.articleId == articleId }
        val updatedCurrentArticleId =
            when {
                state.currentArticleId != articleId -> state.currentArticleId
                updatedItems.isEmpty() -> null
                currentIndex == null -> null
                removedIndex < updatedItems.size -> updatedItems[removedIndex].articleId
                else -> updatedItems.last().articleId
            }

        return state.copy(
            items = updatedItems,
            currentArticleId = updatedCurrentArticleId,
            bookmarks = state.bookmarks - articleId,
            playbackState =
                if (updatedCurrentArticleId == null) TtsQueuePlaybackState.Idle else state.playbackState,
            currentSegmentStartedAtMillis = if (updatedCurrentArticleId == state.currentArticleId) state.currentSegmentStartedAtMillis else null,
            currentSegmentDurationMs = if (updatedCurrentArticleId == state.currentArticleId) state.currentSegmentDurationMs else 0,
        )
    }

    fun clear(state: TtsQueueState): TtsQueueState =
        state.copy(
            items = emptyList(),
            currentArticleId = null,
            playbackState = TtsQueuePlaybackState.Idle,
            bookmarks = emptyMap(),
            sleepTimer = TtsSleepTimerState(),
            currentSegmentStartedAtMillis = null,
            currentSegmentDurationMs = 0,
        )

    fun moveUp(state: TtsQueueState, articleId: String): TtsQueueState {
        val index = state.items.indexOfFirst { it.articleId == articleId }
        if (index <= 0) return state
        val items = state.items.toMutableList()
        items[index - 1] = items[index].also { items[index] = items[index - 1] }
        return state.copy(items = items)
    }

    fun moveDown(state: TtsQueueState, articleId: String): TtsQueueState {
        val index = state.items.indexOfFirst { it.articleId == articleId }
        if (index == -1 || index >= state.items.lastIndex) return state
        val items = state.items.toMutableList()
        items[index + 1] = items[index].also { items[index] = items[index + 1] }
        return state.copy(items = items)
    }
}
