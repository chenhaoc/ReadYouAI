package me.ash.reader.infrastructure.android.ttsqueue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TtsTimeEstimateTest {

    @Test
    fun estimateReadingStatsFromCharCount_rounds_reading_and_audio_minutes() {
        val stats = estimateReadingStatsFromCharCount(charCount = 4_800)

        requireNotNull(stats)
        assertEquals(4_800, stats.charCount)
        assertEquals(24, stats.readingMinutes)
        assertEquals(14, stats.audioMinutes)
    }

    @Test
    fun estimateReadingStatsFromCharCount_returns_null_for_empty_content() {
        assertNull(estimateReadingStatsFromCharCount(charCount = 0))
    }

    @Test
    fun segmentCharCountsToDurationEstimate_calculates_current_and_total_duration() {
        val estimate =
            segmentCharCountsToDurationEstimate(
                currentSegmentIndex = 2,
                segmentCharCounts = listOf(10, 20, 30),
            )

        requireNotNull(estimate)
        assertEquals(charsToMs(30), estimate.currentMs)
        assertEquals(charsToMs(60), estimate.totalMs)
    }
}
