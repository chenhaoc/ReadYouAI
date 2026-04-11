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
}
