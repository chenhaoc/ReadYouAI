package me.ash.reader.infrastructure.android.ttsqueue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun playNow_moves_existing_item_to_head_and_marks_it_current() {
        var state = TtsQueueState(items = listOf(item("a"), item("b")), currentArticleId = "a")

        state = TtsQueueReducer.playNow(state, item("b"))

        assertEquals(listOf("b", "a"), state.items.map(TtsQueueItem::articleId))
        assertEquals("b", state.currentArticleId)
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

    private fun item(id: String) =
        TtsQueueItem(
            articleId = id,
            title = "title-$id",
            feedName = "feed-$id",
            imageUrl = null,
        )
}
