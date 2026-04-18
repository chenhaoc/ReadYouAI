package me.ash.reader.ui.page.home.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatMarkdownSupportTest {

    @Test
    fun parseAiChatMarkdownBlocks_supportsBasicMarkdownBlocks() {
        val blocks =
            parseAiChatMarkdownBlocks(
                """
                # 标题

                **加粗** 和 *斜体* 以及 `代码`

                - 列表一
                - 列表二

                > 引用内容
                """.trimIndent()
            )

        assertEquals(4, blocks.size)
        assertTrue(blocks[0] is AiChatMarkdownBlock.Heading)
        assertTrue(blocks[1] is AiChatMarkdownBlock.Paragraph)
        assertTrue(blocks[2] is AiChatMarkdownBlock.ListBlock)
        assertTrue(blocks[3] is AiChatMarkdownBlock.Quote)
    }

    @Test
    fun parseAiChatMarkdownBlocks_supportsNestedTaskListsDividerCodeBlockAndTable() {
        val blocks =
            parseAiChatMarkdownBlocks(
                """
                - 父项
                  - [x] 子任务
                    1. 孙项

                ---

                ```kotlin
                val answer = 42
                ```

                | 名称 | 值 |
                | --- | --- |
                | a | 1 |
                """.trimIndent()
            )

        val list = blocks[0] as AiChatMarkdownBlock.ListBlock
        assertEquals(3, list.items.size)
        assertEquals(0, list.items[0].depth)
        assertEquals(1, list.items[1].depth)
        assertTrue(list.items[1].isTask)
        assertEquals(2, list.items[2].depth)
        assertTrue(blocks[1] is AiChatMarkdownBlock.Divider)
        assertTrue(blocks[2] is AiChatMarkdownBlock.CodeBlock)
        assertTrue(blocks[3] is AiChatMarkdownBlock.Table)
    }

    @Test
    fun buildAiChatMarkdownHtml_supportsLinksAndInlineStyles() {
        val html =
            buildAiChatMarkdownHtml(
                """
                ~~删除线~~ ==高亮== ***粗斜体*** **粗体** *斜体* `代码` [OpenAI](https://openai.com)
                """.trimIndent()
            )

        assertTrue(html.contains("<del>删除线</del>"))
        assertTrue(html.contains("<mark>高亮</mark>"))
        assertTrue(html.contains("<strong><em>粗斜体</em></strong>"))
        assertTrue(html.contains("<strong>粗体</strong>"))
        assertTrue(html.contains("<em>斜体</em>"))
        assertTrue(html.contains("<code>代码</code>"))
        assertTrue(html.contains("""<a href="https://openai.com">OpenAI</a>"""))
    }

    @Test
    fun buildAiChatMarkdownHtml_supportsBlockRendering() {
        val html =
            buildAiChatMarkdownHtml(
                """
                # 标题

                - 父项
                  - [x] 子任务
                    1. 孙项

                > 引用内容

                ```kotlin
                val answer = 42
                ```
                """.trimIndent()
            )

        assertTrue(html.contains("<h1>标题</h1>"))
        assertTrue(html.contains("""<span class="ry-ai-chat-task-marker">☑</span>"""))
        assertTrue(html.contains("<blockquote><p>引用内容</p></blockquote>"))
        assertTrue(html.contains("""<div class="ry-ai-chat-code-language">kotlin</div>"""))
        assertTrue(html.contains("""<code class="language-kotlin">val answer = 42</code>"""))
    }
}
