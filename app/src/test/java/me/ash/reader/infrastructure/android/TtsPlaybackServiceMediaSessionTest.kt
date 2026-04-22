package me.ash.reader.infrastructure.android

import android.support.v4.media.session.PlaybackStateCompat
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueueItem
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueuePlaybackState
import me.ash.reader.infrastructure.android.ttsqueue.TtsQueueState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsPlaybackServiceMediaSessionTest {

    @Test
    fun queueWithCurrentArticle_keeps_headset_transport_actions_available() {
        val state =
            TtsQueueState(
                items = listOf(queueItem("a")),
                currentArticleId = "a",
                playbackState = TtsQueuePlaybackState.Idle,
            )

        val actions = state.supportedMediaSessionActions()

        assertTrue(state.hasActiveMediaSession())
        assertEquals(PlaybackStateCompat.STATE_PAUSED, state.toMediaSessionPlaybackState())
        assertTrue(actions and PlaybackStateCompat.ACTION_PLAY != 0L)
        assertTrue(actions and PlaybackStateCompat.ACTION_PAUSE != 0L)
        assertTrue(actions and PlaybackStateCompat.ACTION_PLAY_PAUSE != 0L)
        assertTrue(actions and PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS != 0L)
        assertTrue(actions and PlaybackStateCompat.ACTION_SKIP_TO_NEXT != 0L)
    }

    @Test
    fun playingQueue_reports_playing_state_for_media_session() {
        val state =
            TtsQueueState(
                items = listOf(queueItem("a")),
                currentArticleId = "a",
                playbackState = TtsQueuePlaybackState.Reading,
            )

        assertTrue(state.isPlaying())
        assertEquals(PlaybackStateCompat.STATE_PLAYING, state.toMediaSessionPlaybackState())
    }

    @Test
    fun emptyQueue_releases_media_session_state() {
        val state = TtsQueueState()

        assertFalse(state.hasActiveMediaSession())
        assertEquals(PlaybackStateCompat.STATE_STOPPED, state.toMediaSessionPlaybackState())
    }

    private fun queueItem(id: String) =
        TtsQueueItem(
            articleId = id,
            title = "title-$id",
            feedName = "feed-$id",
        )
}
