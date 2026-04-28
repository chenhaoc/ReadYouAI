package me.ash.reader.ui.page.home.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleTranslationRendererTest {

    @Test
    fun buildWebViewBilingualContent_returnsOriginalContentWhenOnlyExtraTranslationExists() {
        val content = """<div class="wrapper"><p>Hello paragraph</p></div>"""
        val translationBlocks =
            """
            [
              {"id":"list_title","translatedText":"标题译文"}
            ]
            """.trimIndent()

        val html =
            buildWebViewBilingualContent(
                content = content,
                blocks = ArticleContentBlockParser.parse(content),
                translatedBlockMap = parseTranslatedBlockMap(translationBlocks),
            )

        assertEquals(content, html)
    }

    @Test
    fun buildWebViewBilingualContent_returnsOriginalContentWhenNoBlocksCanBeParsed() {
        val content = """<custom-tag><br /></custom-tag>"""
        val translationBlocks =
            """
            [
              {"id":"paragraph_1","translatedText":"段落译文"}
            ]
            """.trimIndent()

        val html =
            buildWebViewBilingualContent(
                content = content,
                blocks = ArticleContentBlockParser.parse(content),
                translatedBlockMap = parseTranslatedBlockMap(translationBlocks),
            )

        assertEquals(content, html)
    }

    @Test
    fun buildWebViewBilingualContent_keepsParagraphAndListTranslationNonItalic() {
        val content =
            """
            <p>Hello paragraph</p>
            <blockquote>Original quote</blockquote>
            <ul><li>First item</li></ul>
            """.trimIndent()
        val translationBlocks =
            """
            [
              {"id":"paragraph_1","translatedText":"普通正文"},
              {"id":"quote_1","translatedText":"引用内容"},
              {"id":"list_item_1","translatedText":"列表项目"}
            ]
            """.trimIndent()

        val html =
            buildWebViewBilingualContent(
                content = content,
                blocks = ArticleContentBlockParser.parse(content),
                translatedBlockMap = parseTranslatedBlockMap(translationBlocks),
            )

        assertTrue(html.contains("""普通正文</p>"""))
        assertTrue(html.contains("""列表项目</p>"""))
        assertTrue(html.contains("""引用内容</p>"""))
        assertFalse(
            html.contains(
                """style="margin: 0 16px 18px; color: var(--link-text-color); font-style: italic;">普通正文</p>"""
            )
        )
        assertFalse(
            html.contains(
                """style="margin: 0 16px 14px 36px; color: var(--link-text-color); font-style: italic;">列表项目</p>"""
            )
        )
    }

    @Test
    fun buildWebViewBilingualContent_onlyQuoteTranslationUsesItalicStyle() {
        val content =
            """
            <p>Hello paragraph</p>
            <blockquote>Original quote</blockquote>
            <ul><li>First item</li></ul>
            """.trimIndent()
        val translationBlocks =
            """
            [
              {"id":"paragraph_1","translatedText":"普通正文"},
              {"id":"quote_1","translatedText":"引用内容"},
              {"id":"list_item_1","translatedText":"列表项目"}
            ]
            """.trimIndent()

        val html =
            buildWebViewBilingualContent(
                content = content,
                blocks = ArticleContentBlockParser.parse(content),
                translatedBlockMap = parseTranslatedBlockMap(translationBlocks),
            )

        assertTrue(
            html.contains(
                """style="margin: 0 16px 18px; color: var(--link-text-color);">普通正文</p>"""
            )
        )
        assertTrue(
            html.contains(
                """style="margin: 0 16px 14px 36px; color: var(--link-text-color);">列表项目</p>"""
            )
        )
        assertTrue(
            html.contains(
                """style="margin: 0 16px 18px; padding-left: 12px; border-left: 3px solid rgba(127,127,127,.35); color: var(--link-text-color); font-style: italic;">引用内容</p>"""
            )
        )
    }
}
