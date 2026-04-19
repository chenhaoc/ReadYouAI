package me.ash.reader.ui.page.home.reading

import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleTranslationSupportTest {

    @Test
    fun normalizeFeedTranslationSettingsKeepsAutoDisabledWhenTranslationIsOff() {
        val normalized = normalizeFeedTranslationSettings(
            isTranslationEnabled = false,
            isAutoTranslate = true,
        )

        assertEquals(
            FeedTranslationSettings(
                isTranslationEnabled = false,
                isAutoTranslate = false,
            ),
            normalized,
        )
    }

    @Test
    fun normalizeFeedTranslationSettingsEnablesTranslationWhenAutoIsEnabled() {
        val normalized = normalizeFeedTranslationSettings(
            isTranslationEnabled = false,
            isAutoTranslate = true,
            preferAutoTranslate = true,
        )

        assertEquals(
            FeedTranslationSettings(
                isTranslationEnabled = true,
                isAutoTranslate = true,
            ),
            normalized,
        )
    }

    @Test
    fun prioritizedTranslationBatchStartsFromPreferredBlock() {
        val blocks =
            listOf(
                ArticleContentBlock("paragraph_1", ArticleContentBlockType.Paragraph, "<p>1</p>", "a".repeat(300)),
                ArticleContentBlock("paragraph_2", ArticleContentBlockType.Paragraph, "<p>2</p>", "b".repeat(300)),
                ArticleContentBlock("paragraph_3", ArticleContentBlockType.Paragraph, "<p>3</p>", "c".repeat(300)),
            )

        val batch =
            buildPrioritizedTranslationBatch(
                blocks = blocks,
                translatedBlockIds = emptySet(),
                preferredStartIndex = 1,
                maxEstimatedOutputTokens = 200,
            )

        assertEquals(listOf("paragraph_2"), batch.map { it.id })
    }

    @Test
    fun nativeTranslationFocusTracksVisibleOriginalAndTranslatedItems() {
        val blocks =
            listOf(
                ArticleContentBlock("paragraph_1", ArticleContentBlockType.Paragraph, "<p>1</p>", "one"),
                ArticleContentBlock("paragraph_2", ArticleContentBlockType.Paragraph, "<p>2</p>", "two"),
                ArticleContentBlock("paragraph_3", ArticleContentBlockType.Paragraph, "<p>3</p>", "three"),
            )

        assertEquals(
            0,
            estimateNativeTranslationFocusIndex(
                firstVisibleItemIndex = 3,
                blocks = blocks,
                translatedBlockIds = setOf("paragraph_1"),
            ),
        )
    }

    @Test
    fun webViewTranslationFocusFollowsScrollProgress() {
        val blocks =
            listOf(
                ArticleContentBlock("heading_1", ArticleContentBlockType.Heading, "<h1>1</h1>", "title"),
                ArticleContentBlock("paragraph_1", ArticleContentBlockType.Paragraph, "<p>1</p>", "one"),
                ArticleContentBlock("image_1", ArticleContentBlockType.Image, "<img />"),
                ArticleContentBlock("paragraph_2", ArticleContentBlockType.Paragraph, "<p>2</p>", "two"),
            )

        assertEquals(
            3,
            estimateWebViewTranslationFocusIndex(
                scrollValue = 80,
                maxScrollValue = 100,
                blocks = blocks,
            ),
        )
    }
}
