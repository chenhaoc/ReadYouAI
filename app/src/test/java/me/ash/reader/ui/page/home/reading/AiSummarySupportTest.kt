package me.ash.reader.ui.page.home.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import me.ash.reader.ui.page.adaptive.ReadingUiState

class AiSummarySupportTest {

    @Test
    fun resolveAiSummarizationPromptFallsBackToDefaultWhenBlank() {
        assertEquals(
            DEFAULT_AI_SUMMARIZATION_PROMPT,
            resolveAiSummarizationPrompt(""),
        )
        assertEquals(
            DEFAULT_AI_SUMMARIZATION_PROMPT,
            resolveAiSummarizationPrompt("   "),
        )
    }

    @Test
    fun resolveAiSummarizationPromptKeepsCustomPrompt() {
        assertEquals(
            "custom prompt",
            resolveAiSummarizationPrompt("custom prompt"),
        )
    }

    @Test
    fun shouldAutoSummarizeRequiresFeedOptInAndEligibleUiState() {
        val eligibleState = ReadingUiState(hasAutoAiSummaryAttempted = false, aiSummary = null)
        val ineligibleState = ReadingUiState(hasAutoAiSummaryAttempted = true, aiSummary = null)

        assertTrue(shouldAutoSummarize(feedAutoSummary = true, state = eligibleState))
        assertFalse(shouldAutoSummarize(feedAutoSummary = false, state = eligibleState))
        assertFalse(shouldAutoSummarize(feedAutoSummary = true, state = ineligibleState))
    }
}
