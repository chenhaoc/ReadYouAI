package me.ash.reader.ui.page.home.reading.queue

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueueController
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueuePlaybackState
import kotlinx.coroutines.flow.StateFlow
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueueState

@HiltViewModel
class TtsQueueOverlayViewModel @Inject constructor(
    private val ttsQueueController: TtsQueueController,
) : ViewModel() {
    val queueState: StateFlow<TtsQueueState> = ttsQueueController.state

    fun stopQueuePlayback() {
        ttsQueueController.stop()
    }

    fun skipQueuePlayback() {
        ttsQueueController.skipToNext()
    }

    fun previousQueuePlayback() {
        ttsQueueController.skipToPrevious()
    }

    fun clearPlaylist() {
        ttsQueueController.clear()
    }

    fun removeFromPlaylist(articleId: String) {
        ttsQueueController.remove(articleId)
    }

    fun movePlaylistItemUp(articleId: String) {
        ttsQueueController.moveUp(articleId)
    }

    fun movePlaylistItemDown(articleId: String) {
        ttsQueueController.moveDown(articleId)
    }

    fun playPlaylistItem(articleId: String) {
        if (
            queueState.value.currentArticleId == articleId &&
            queueState.value.playbackState == TtsQueuePlaybackState.Reading
        ) {
            stopQueuePlayback()
            return
        }
        queueState.value.items.firstOrNull { it.articleId == articleId }?.let {
            if (queueState.value.currentArticleId == articleId) {
                ttsQueueController.resumeCurrent()
            } else {
                ttsQueueController.playNow(it)
            }
        }
    }

    fun seekCurrentPlayback(segmentIndex: Int) {
        ttsQueueController.seekCurrent(segmentIndex)
    }

    fun toggleQueuePlayback() {
        when (queueState.value.playbackState) {
            TtsQueuePlaybackState.Idle,
            TtsQueuePlaybackState.Error -> {
                queueState.value.currentItem?.let { ttsQueueController.resumeCurrent() }
            }

            TtsQueuePlaybackState.Preparing -> Unit
            TtsQueuePlaybackState.Reading -> stopQueuePlayback()
        }
    }
}
