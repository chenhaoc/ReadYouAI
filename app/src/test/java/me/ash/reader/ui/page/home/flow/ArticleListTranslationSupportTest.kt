package me.ash.reader.ui.page.home.flow

import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleListTranslationSupportTest {

    @Test
    fun buildListTranslationTargetIdsKeepsVisibleArticlesAheadOfPrefetchWindow() {
        val targetIds =
            buildListTranslationTargetIds(
                visibleArticleIds = listOf("a2", "a3"),
                subsequentArticleIds = listOf("a4", "a5", "a6", "a7", "a8"),
                prefetchCount = 4,
            )

        assertEquals(listOf("a2", "a3", "a4", "a5", "a6", "a7"), targetIds)
    }

    @Test
    fun buildListTranslationTargetIdsWaitsUntilVisibleArticlesAreKnown() {
        val targetIds =
            buildListTranslationTargetIds(
                visibleArticleIds = emptyList(),
                subsequentArticleIds = listOf("a1", "a2", "a3"),
            )

        assertEquals(emptyList<String>(), targetIds)
    }

    @Test
    fun translatedListPreviewUsesFirstBlockAsTitleAndRemainingBlocksAsSummary() {
        val preview =
            resolveTranslatedListPreview(
                translationBlocks =
                    """
                    [
                      {"id":"h1","translatedText":"中文标题"},
                      {"id":"p1","translatedText":"第一段摘要"},
                      {"id":"p2","translatedText":"第二段摘要"}
                    ]
                    """.trimIndent(),
                fallbackTitle = "Original Title",
                fallbackDescription = "Original Description",
            )

        assertEquals("中文标题", preview.title)
        assertEquals("第一段摘要 第二段摘要", preview.shortDescription)
    }
}
