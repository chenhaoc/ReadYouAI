package me.ash.reader.ui.page.adaptive

import java.util.Date
import me.ash.reader.domain.model.article.Article
import me.ash.reader.domain.model.article.ArticleWithFeed
import me.ash.reader.domain.model.feed.Feed
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
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
                translatableBlockCount = 3,
            ).shouldAutoGenerateTranslation
        )
        assertFalse(
            ReadingUiState(
                hasAutoTranslationAttempted = true,
                translatedContentBlocks = null,
                translatableBlockCount = 3,
            ).shouldAutoGenerateTranslation
        )
        assertFalse(
            ReadingUiState(
                hasAutoTranslationAttempted = false,
                translatedContentBlocks = "[]",
                translatableBlockCount = 3,
                translatedBlockCount = 3,
            ).shouldAutoGenerateTranslation
        )
    }

    @Test
    fun autoTranslationShouldContinueWhenCachedTranslationIsPartial() {
        val state =
            ReadingUiState(
                hasAutoTranslationAttempted = false,
                translatedContentBlocks = "[{\"id\":\"p1\",\"translatedText\":\"x\"}]",
                translatedBlockCount = 1,
                translatableBlockCount = 3,
            )

        assertTrue(state.shouldAutoGenerateTranslation)
    }

    @Test
    fun withPendingAiSummaryOverridesStoredSummaryWhenPendingValueExists() {
        val article = articleWithFeed(aiSummary = null)

        val updated = article.withPendingAiSummary("pending")

        assertNotSame(article, updated)
        assertTrue(updated.article.aiSummary == "pending")
    }

    @Test
    fun withPendingAiSummaryKeepsOriginalWhenPendingValueMatchesStoredSummary() {
        val article = articleWithFeed(aiSummary = "ready")

        val updated = article.withPendingAiSummary("ready")

        assertSame(article, updated)
    }

    private fun articleWithFeed(aiSummary: String?): ArticleWithFeed =
        ArticleWithFeed(
            article =
                Article(
                    id = "article-1",
                    date = Date(0L),
                    title = "title",
                    rawDescription = "",
                    shortDescription = "",
                    link = "https://example.com/article-1",
                    feedId = "feed-1",
                    accountId = 1,
                    aiSummary = aiSummary,
                ),
            feed =
                Feed(
                    id = "feed-1",
                    name = "Feed",
                    url = "https://example.com/feed.xml",
                    groupId = "group-1",
                    accountId = 1,
                ),
        )
}
