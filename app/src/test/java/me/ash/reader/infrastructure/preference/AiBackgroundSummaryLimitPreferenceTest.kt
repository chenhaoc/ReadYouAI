package me.ash.reader.infrastructure.preference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class AiBackgroundSummaryLimitPreferenceTest {
    @Test
    fun defaultLimitIsTwentyFive() {
        assertEquals(25, AiBackgroundSummaryLimitPreference.default.value)
        assertEquals(25, AiBackgroundSummaryLimitPreference.default.limit)
    }

    @Test
    fun unlimitedHasNoLimit() {
        assertSame(AiBackgroundSummaryLimitPreference.Unlimited, AiBackgroundSummaryLimitPreference.fromValue(-1))
        assertNull(AiBackgroundSummaryLimitPreference.Unlimited.limit)
    }

    @Test
    fun unknownValueFallsBackToDefault() {
        assertSame(AiBackgroundSummaryLimitPreference.default, AiBackgroundSummaryLimitPreference.fromValue(999))
    }
}
