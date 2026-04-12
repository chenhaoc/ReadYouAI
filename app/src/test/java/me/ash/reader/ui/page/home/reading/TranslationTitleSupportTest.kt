package me.ash.reader.ui.page.home.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import me.ash.reader.domain.repository.TranslatedArticleBlock

class TranslationTitleSupportTest {

    @Test
    fun resolveTranslatedTitleReturnsDedicatedTitleBlock() {
        val translatedTitle =
            resolveTranslatedTitle(
                """
                [
                  {"id":"paragraph_1","translatedText":"第一段"},
                  {"id":"list_title","translatedText":"中文标题"}
                ]
                """.trimIndent()
            )

        assertEquals("中文标题", translatedTitle)
    }

    @Test
    fun resolveTranslatedTitleReturnsNullWhenTitleBlockIsMissing() {
        val translatedTitle =
            resolveTranslatedTitle(
                """
                [
                  {"id":"paragraph_1","translatedText":"第一段"}
                ]
                """.trimIndent()
            )

        assertNull(translatedTitle)
    }

    @Test
    fun selectExtraTranslationsKeepsDedicatedTitleBlockOutsideArticleBodyBlocks() {
        val blocks =
            listOf(
                ArticleContentBlock("paragraph_1", ArticleContentBlockType.Paragraph, "<p>Lead</p>", "Lead")
            )
        val storedBlocks =
            listOf(
                TranslatedArticleBlock("list_title", "中文标题"),
                TranslatedArticleBlock("paragraph_1", "第一段"),
            )

        assertEquals(
            listOf("list_title"),
            selectExtraTranslations(blocks = blocks, storedBlocks = storedBlocks).map { it.id },
        )
    }
}
