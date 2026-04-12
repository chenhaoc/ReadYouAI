package me.ash.reader.ui.page.home.reading.queue

import org.junit.Assert.assertEquals
import org.junit.Test

class TtsFloatingPlayerButtonStateTest {

    @Test
    fun resolveDockSide_prefers_left_when_button_center_is_before_midpoint() {
        assertEquals(
            TtsFloatingButtonDockSide.Left,
            resolveDockSideFromOffset(
                offsetPx = 40f,
                containerWidthPx = 400f,
                buttonWidthPx = 56f,
            ),
        )
    }

    @Test
    fun resolveDockSide_prefers_right_when_button_center_is_after_midpoint() {
        assertEquals(
            TtsFloatingButtonDockSide.Right,
            resolveDockSideFromOffset(
                offsetPx = 260f,
                containerWidthPx = 400f,
                buttonWidthPx = 56f,
            ),
        )
    }
}
