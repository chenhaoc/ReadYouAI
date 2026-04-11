package me.ash.reader.infrastructure.android.ttsqueue

data class TtsQueueItem(
    val articleId: String,
    val title: String,
    val feedName: String,
    val imageUrl: String? = null,
    val htmlContent: String? = null,
)

enum class TtsQueuePlaybackState {
    Idle,
    Preparing,
    Reading,
    Error,
}

data class TtsQueueState(
    val items: List<TtsQueueItem> = emptyList(),
    val currentArticleId: String? = null,
    val playbackState: TtsQueuePlaybackState = TtsQueuePlaybackState.Idle,
) {
    val currentIndex: Int?
        get() = items.indexOfFirst { it.articleId == currentArticleId }.takeIf { it >= 0 }

    val currentItem: TtsQueueItem?
        get() = currentIndex?.let(items::getOrNull)
}

object TtsQueueReducer {

    fun append(state: TtsQueueState, item: TtsQueueItem): TtsQueueState {
        if (state.items.any { it.articleId == item.articleId }) return state
        return state.copy(items = state.items + item)
    }

    fun playNow(state: TtsQueueState, item: TtsQueueItem): TtsQueueState {
        val withoutItem = state.items.filterNot { it.articleId == item.articleId }
        return state.copy(
            items = listOf(item) + withoutItem,
            currentArticleId = item.articleId,
        )
    }

    fun advance(state: TtsQueueState): TtsQueueState {
        val currentIndex = state.currentIndex ?: return state
        val nextItem = state.items.getOrNull(currentIndex + 1)
        return state.copy(
            currentArticleId = nextItem?.articleId,
            playbackState = if (nextItem == null) TtsQueuePlaybackState.Idle else state.playbackState,
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
            playbackState =
                if (updatedCurrentArticleId == null) TtsQueuePlaybackState.Idle else state.playbackState,
        )
    }

    fun clear(state: TtsQueueState): TtsQueueState =
        state.copy(
            items = emptyList(),
            currentArticleId = null,
            playbackState = TtsQueuePlaybackState.Idle,
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
