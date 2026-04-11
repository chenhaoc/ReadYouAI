package me.ash.reader.ui.page.adaptive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingUiStateTest {

    @Test
    fun aiSummaryCardHiddenWhenNoSummaryOrTransientStateExists() {
        val state = ReadingUiState()

        assertFalse(state.isAiSummaryVisible)
    }

    @Test
    fun aiSummaryCardVisibleWhenSummaryExists() {
        val state = ReadingUiState(aiSummary = "summary", shouldRenderAiSummaryInline = true)

        assertTrue(state.isAiSummaryVisible)
    }

    @Test
    fun aiSummaryCardVisibleWhenLoadingOrErrorExists() {
        assertTrue(
            ReadingUiState(
                isAiSummaryInlineLoading = true,
                shouldRenderAiSummaryInline = true,
            ).isAiSummaryVisible
        )
        assertTrue(
            ReadingUiState(
                aiSummaryError = "boom",
                shouldRenderAiSummaryInline = true,
            ).isAiSummaryVisible
        )
    }

    @Test
    fun aiSummaryPromptVisibleWhenSummaryReadyButNotViewed() {
        val state = ReadingUiState(aiSummary = "summary", shouldShowAiSummaryReadyPrompt = true)

        assertTrue(state.shouldShowAiSummaryReadyPrompt)
        assertFalse(state.isAiSummaryVisible)
    }

    @Test
    fun autoSummaryShouldRunOnlyWhenEnabledArticleHasNoSummaryAndWasNotTried() {
        assertTrue(
            ReadingUiState(hasAutoAiSummaryAttempted = false, aiSummary = null)
                .shouldAutoGenerateAiSummary
        )
        assertFalse(
            ReadingUiState(hasAutoAiSummaryAttempted = true, aiSummary = null)
                .shouldAutoGenerateAiSummary
        )
        assertFalse(
            ReadingUiState(hasAutoAiSummaryAttempted = false, aiSummary = "summary")
                .shouldAutoGenerateAiSummary
        )
    }

    @Test
    fun translationVisibleWhenBilingualContentExistsOrIsLoading() {
        assertTrue(
            ReadingUiState(
                translatedContentBlocks = "[]",
                shouldRenderTranslationInline = true,
            ).isTranslationVisible
        )
        assertTrue(
            ReadingUiState(
                isTranslationInlineLoading = true,
                shouldRenderTranslationInline = true,
            ).isTranslationVisible
        )
    }

    @Test
    fun autoTranslationShouldRunOnlyWhenNoCachedTranslationExistsAndNotYetAttempted() {
        assertTrue(
            ReadingUiState(
                hasAutoTranslationAttempted = false,
                translatedContentBlocks = null,
            ).shouldAutoGenerateTranslation
        )
        assertFalse(
            ReadingUiState(
                hasAutoTranslationAttempted = true,
                translatedContentBlocks = null,
            ).shouldAutoGenerateTranslation
        )
        assertFalse(
            ReadingUiState(
                hasAutoTranslationAttempted = false,
                translatedContentBlocks = "[]",
            ).shouldAutoGenerateTranslation
        )
    }
}
