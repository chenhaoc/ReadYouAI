package me.ash.reader.ui.page.home.reading.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun verticalOffsetRange_reserves_top_and_bottom_safe_space() {
        val range =
            verticalButtonOffsetRangePx(
                containerHeightPx = 800f,
                buttonHeightPx = 56f,
                topInsetPx = 24f,
                bottomInsetPx = 16f,
                bottomPaddingPx = 88f,
                edgePaddingPx = 16f,
            )

        assertEquals(40f, range.start)
        assertEquals(624f, range.endInclusive)
    }

    @Test
    fun verticalRatio_round_trips_through_offset_mapping() {
        val offset =
            resolveVerticalOffsetFromRatio(
                verticalRatio = 0.25f,
                minOffsetPx = 40f,
                maxOffsetPx = 640f,
            )

        assertEquals(190f, offset)
        assertEquals(
            0.25f,
            resolveVerticalRatioFromOffset(
                offsetPx = offset,
                minOffsetPx = 40f,
                maxOffsetPx = 640f,
            ),
        )
    }

    @Test
    fun collapsedVerticalRange_defaults_to_bottom_ratio() {
        assertTrue(
            resolveVerticalRatioFromOffset(
                offsetPx = 48f,
                minOffsetPx = 48f,
                maxOffsetPx = 48f,
            ) == 1f,
        )
    }
}
