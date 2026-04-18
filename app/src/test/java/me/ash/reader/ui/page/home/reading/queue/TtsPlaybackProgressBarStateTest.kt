package me.ash.reader.ui.page.home.reading.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsPlaybackProgressBarStateTest {

    @Test
    fun seek_bar_is_enabled_only_when_multiple_segments_exist() {
        assertFalse(canSeekSegments(emptyList()))
        assertFalse(canSeekSegments(listOf(120)))
        assertTrue(canSeekSegments(listOf(120, 80)))
    }

    @Test
    fun progress_fraction_uses_weighted_segment_lengths() {
        val segmentCharCounts = listOf(20, 30, 50)

        assertEquals(0f, weightedProgressFraction(currentSegmentIndex = 0, segmentCharCounts = segmentCharCounts))
        assertEquals(0.2f, weightedProgressFraction(currentSegmentIndex = 1, segmentCharCounts = segmentCharCounts))
        assertEquals(0.5f, weightedProgressFraction(currentSegmentIndex = 2, segmentCharCounts = segmentCharCounts))
    }


    @Test
    fun progress_fraction_includes_current_segment_progress() {
        val segmentCharCounts = listOf(20, 30, 50)

        assertEquals(
            0.35f,
            weightedProgressFraction(
                currentSegmentIndex = 1,
                segmentCharCounts = segmentCharCounts,
                currentSegmentProgressFraction = 0.5f,
            ),
        )
    }

    @Test
    fun target_segment_uses_weighted_char_positions() {
        val segmentCharCounts = listOf(20, 30, 50)

        assertEquals(0, weightedSegmentIndexFromFraction(0f, segmentCharCounts = segmentCharCounts))
        assertEquals(1, weightedSegmentIndexFromFraction(0.35f, segmentCharCounts = segmentCharCounts))
        assertEquals(2, weightedSegmentIndexFromFraction(0.8f, segmentCharCounts = segmentCharCounts))
    }
}
