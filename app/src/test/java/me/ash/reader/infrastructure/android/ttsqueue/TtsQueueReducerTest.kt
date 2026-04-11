package me.ash.reader.infrastructure.android.ttsqueue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsQueueReducerTest {

    @Test
    fun append_adds_new_item_to_tail_without_duplicates() {
        var state = TtsQueueState()

        state = TtsQueueReducer.append(state, item("a"))
        state = TtsQueueReducer.append(state, item("b"))
        state = TtsQueueReducer.append(state, item("a"))

        assertEquals(listOf("a", "b"), state.items.map(TtsQueueItem::articleId))
        assertNull(state.currentArticleId)
    }

    @Test
    fun playNow_keeps_existing_order_and_marks_item_current() {
        var state = TtsQueueState(items = listOf(item("a"), item("b")), currentArticleId = "a")

        state = TtsQueueReducer.playNow(state, item("b"))

        assertEquals(listOf("a", "b"), state.items.map(TtsQueueItem::articleId))
        assertEquals("b", state.currentArticleId)
    }

    @Test
    fun playNow_appends_missing_item_without_reordering_existing_items() {
        val state = TtsQueueState(items = listOf(item("a"), item("b")), currentArticleId = "a")

        val updated = TtsQueueReducer.playNow(state, item("c"))

        assertEquals(listOf("a", "b", "c"), updated.items.map(TtsQueueItem::articleId))
        assertEquals("c", updated.currentArticleId)
    }

    @Test
    fun advance_selects_next_item_when_current_completes() {
        val state =
            TtsQueueState(
                items = listOf(item("a"), item("b"), item("c")),
                currentArticleId = "a",
            )

        val advanced = TtsQueueReducer.advance(state)

        assertEquals("b", advanced.currentArticleId)
    }

    @Test
    fun remove_current_item_selects_next_when_available() {
        val state =
            TtsQueueState(
                items = listOf(item("a"), item("b"), item("c")),
                currentArticleId = "b",
            )

        val updated = TtsQueueReducer.remove(state, "b")

        assertEquals(listOf("a", "c"), updated.items.map(TtsQueueItem::articleId))
        assertEquals("c", updated.currentArticleId)
    }

    @Test
    fun remove_last_remaining_item_clears_current() {
        val state =
            TtsQueueState(
                items = listOf(item("a")),
                currentArticleId = "a",
            )

        val updated = TtsQueueReducer.remove(state, "a")

        assertEquals(emptyList<String>(), updated.items.map(TtsQueueItem::articleId))
        assertNull(updated.currentArticleId)
    }

    @Test
    fun remove_drops_bookmark_for_removed_article() {
        val state =
            TtsQueueState(
                items = listOf(item("a"), item("b")),
                currentArticleId = "a",
                bookmarks =
                    mapOf(
                        "a" to TtsPlaybackBookmark(articleId = "a", segmentIndex = 1, segmentCharCounts = listOf(20, 30)),
                        "b" to TtsPlaybackBookmark(articleId = "b", segmentIndex = 0, segmentCharCounts = listOf(50)),
                    ),
            )

        val updated = TtsQueueReducer.remove(state, "a")

        assertEquals(listOf("b"), updated.items.map(TtsQueueItem::articleId))
        assertEquals(setOf("b"), updated.bookmarks.keys)
    }

    @Test
    fun clear_resets_queue_and_bookmarks() {
        val state =
            TtsQueueState(
                items = listOf(item("a")),
                currentArticleId = "a",
                playbackState = TtsQueuePlaybackState.Reading,
                bookmarks =
                    mapOf(
                        "a" to TtsPlaybackBookmark(articleId = "a", segmentIndex = 1, segmentCharCounts = listOf(20, 30)),
                    ),
            )

        val cleared = TtsQueueReducer.clear(state)

        assertTrue(cleared.items.isEmpty())
        assertNull(cleared.currentArticleId)
        assertTrue(cleared.bookmarks.isEmpty())
        assertEquals(TtsQueuePlaybackState.Idle, cleared.playbackState)
    }

    private fun item(id: String) =
        TtsQueueItem(
            articleId = id,
            title = "title-$id",
            feedName = "feed-$id",
            imageUrl = null,
        )
}
