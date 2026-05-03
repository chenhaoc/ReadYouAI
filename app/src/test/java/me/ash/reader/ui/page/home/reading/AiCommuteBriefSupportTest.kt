package me.ash.reader.ui.page.home.reading

import org.junit.Assert.assertEquals
import org.junit.Test

class AiCommuteBriefSupportTest {

    @Test
    fun resolveAiCommuteBriefRecommendationPromptFallsBackToDefaultWhenBlank() {
        assertEquals(
            DEFAULT_AI_COMMUTE_BRIEF_RECOMMENDATION_PROMPT,
            resolveAiCommuteBriefRecommendationPrompt(""),
        )
        assertEquals(
            DEFAULT_AI_COMMUTE_BRIEF_RECOMMENDATION_PROMPT,
            resolveAiCommuteBriefRecommendationPrompt("   "),
        )
    }

    @Test
    fun resolveAiCommuteBriefRecommendationPromptKeepsCustomPrompt() {
        assertEquals(
            "custom prompt",
            resolveAiCommuteBriefRecommendationPrompt("custom prompt"),
        )
    }
}
