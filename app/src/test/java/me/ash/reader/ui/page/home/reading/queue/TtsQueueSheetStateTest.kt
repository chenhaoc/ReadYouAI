package me.ash.reader.ui.page.home.reading.queue

import me.ash.reader.infrastructure.android.ttsqueue.TtsQueuePlaybackState
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueueState
import org.junit.Assert.assertEquals
import org.junit.Test

class TtsQueueSheetStateTest {

    @Test
    fun current_reading_item_uses_pause_control() {
        val control =
            resolveQueueItemControl(
                state =
                    TtsQueueState(
                        currentArticleId = "a",
                        playbackState = TtsQueuePlaybackState.Reading,
                    ),
                articleId = "a",
            )

        assertEquals(TtsQueueItemControl.Pause, control)
    }

    @Test
    fun current_idle_item_uses_play_control() {
        val control =
            resolveQueueItemControl(
                state =
                    TtsQueueState(
                        currentArticleId = "a",
                        playbackState = TtsQueuePlaybackState.Idle,
                    ),
                articleId = "a",
            )

        assertEquals(TtsQueueItemControl.Play, control)
    }
}
