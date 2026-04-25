package me.ash.reader.ui.page.home.reading.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.ash.reader.infrastructure.android.ttsqueue.CommuteBriefBuildResult
import me.ash.reader.infrastructure.android.ttsqueue.CommuteBriefQueueBuilder
import me.ash.reader.infrastructure.android.ttsqueue.TtsCommuteQueueGenerationMode
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueueController
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueueMode
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueuePlaybackState
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueueState
import me.ash.reader.infrastructure.android.ttsqueue.TtsSleepTimerOption

@HiltViewModel
class TtsQueueOverlayViewModel @Inject constructor(
    private val ttsQueueController: TtsQueueController,
    private val commuteBriefQueueBuilder: CommuteBriefQueueBuilder,
) : ViewModel() {
    val queueState: StateFlow<TtsQueueState> = ttsQueueController.state
    private val _commuteBuildResult = MutableStateFlow<CommuteBriefBuildResult?>(null)
    val commuteBuildResult = _commuteBuildResult.asStateFlow()
    private val _commuteBuildGenerationMode = MutableStateFlow<TtsCommuteQueueGenerationMode?>(null)
    val commuteBuildGenerationMode = _commuteBuildGenerationMode.asStateFlow()

    fun switchMode(mode: TtsQueueMode) {
        ttsQueueController.switchMode(mode)
    }

    fun generateCommuteBrief(
        generationMode: TtsCommuteQueueGenerationMode = TtsCommuteQueueGenerationMode.NewestFirst,
    ) {
        if (_commuteBuildGenerationMode.value != null) return
        viewModelScope.launch {
            _commuteBuildGenerationMode.value = generationMode
            try {
                val result = commuteBriefQueueBuilder.build(generationMode)
                _commuteBuildResult.value = result
                if (result.hasSources) {
                    ttsQueueController.replaceCommuteQueue(result.items, result.meta)
                    ttsQueueController.switchMode(TtsQueueMode.Commute)
                }
            } finally {
                _commuteBuildGenerationMode.value = null
            }
        }
    }

    fun clearCommuteBuildResult() {
        _commuteBuildResult.value = null
    }

    fun stopQueuePlayback() {
        ttsQueueController.pause()
    }

    fun skipQueuePlayback() {
        ttsQueueController.skipToNext()
    }

    fun previousQueuePlayback() {
        ttsQueueController.skipToPrevious()
    }


    fun previousQueueSegment() {
        ttsQueueController.skipToPreviousSegment()
    }


    fun nextQueueSegment() {
        ttsQueueController.skipToNextSegment()
    }

    fun setSleepTimer(option: TtsSleepTimerOption) {
        ttsQueueController.setSleepTimer(option)
    }

    fun toggleCurrentStarred() {
        ttsQueueController.toggleCurrentStarred()
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
                ttsQueueController.playNow(it, queueState.value.mode)
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
