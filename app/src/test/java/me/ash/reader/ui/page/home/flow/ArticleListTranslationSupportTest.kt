package me.ash.reader.ui.page.home.flow

import org.junit.Assert.assertEquals
import org.junit.Test
import me.ash.reader.ui.page.home.reading.ArticleContentBlock
import me.ash.reader.ui.page.home.reading.ArticleContentBlockType

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
    fun translatedListPreviewUsesDedicatedTitleAndFirstParagraphAsSummary() {
        val preview =
            resolveTranslatedListPreview(
                translationBlocks =
                    """
                    [
                      {"id":"heading_1","translatedText":"导语标题"},
                      {"id":"paragraph_1","translatedText":"第一段摘要"},
                      {"id":"list_title","translatedText":"中文标题"},
                      {"id":"p2","translatedText":"第二段摘要"}
                    ]
                    """.trimIndent(),
                fallbackTitle = "Original Title",
                fallbackDescription = "Original Description",
            )

        assertEquals("中文标题", preview.title)
        assertEquals("第一段摘要", preview.shortDescription)
    }

    @Test
    fun listTranslationSourceBlocksUseArticleTitleAndFirstParagraph() {
        val sourceBlocks =
            buildListTranslationSourceBlocks(
                articleTitle = "Original Title",
                blocks =
                    listOf(
                        ArticleContentBlock("heading_1", ArticleContentBlockType.Heading, "<h1>Heading</h1>", "Heading"),
                        ArticleContentBlock("paragraph_1", ArticleContentBlockType.Paragraph, "<p>Lead</p>", "Lead"),
                        ArticleContentBlock("paragraph_2", ArticleContentBlockType.Paragraph, "<p>Body</p>", "Body"),
                    ),
            )

        assertEquals(listOf("list_title", "paragraph_1"), sourceBlocks.map { it.id })
    }
}
