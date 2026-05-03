package me.ash.reader.ui.page.home.reading

import me.ash.reader.domain.model.ai.AiChatMessage
import org.junit.Assert.assertFalse
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
    fun buildAiChatMarkdownHtml_supportsBareAndAngleBracketLinks() {
        val html =
            buildAiChatMarkdownHtml(
                """
                访问 https://openai.com/docs 获取文档，或看 <https://example.com/article>.
                """.trimIndent()
            )

        assertTrue(html.contains("""<a href="https://openai.com/docs">https://openai.com/docs</a>"""))
        assertTrue(html.contains("""<a href="https://example.com/article">https://example.com/article</a>."""))
    }

    @Test
    fun buildAiChatMarkdownHtml_doesNotAutolinkBareUrlsInsideLinkLabels() {
        val html =
            buildAiChatMarkdownHtml(
                """
                [https://openai.com](https://example.com)
                """.trimIndent()
            )

        assertTrue(html.contains("""<a href="https://example.com">https://openai.com</a>"""))
        assertFalse(html.contains("""<a href="https://openai.com">"""))
    }

    @Test
    fun buildAiChatMarkdownHtml_preservesBalancedTrailingParenthesesInBareUrls() {
        val html =
            buildAiChatMarkdownHtml(
                """
                参考 https://en.wikipedia.org/wiki/Foo_(bar)
                """.trimIndent()
            )

        assertTrue(html.contains("""<a href="https://en.wikipedia.org/wiki/Foo_(bar)">https://en.wikipedia.org/wiki/Foo_(bar)</a>"""))
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

    @Test
    fun buildAiChatConversationHtml_wrapsUserAssistantAndPendingMessagesIntoSingleDocument() {
        val html =
            buildAiChatConversationHtml(
                messages =
                    listOf(
                        AiChatMessage(
                            articleId = "article-1",
                            role = AI_CHAT_ROLE_USER,
                            content = "用户提问",
                            contextType = AI_CHAT_CONTEXT_MANUAL,
                        ),
                        AiChatMessage(
                            articleId = "article-1",
                            role = AI_CHAT_ROLE_ASSISTANT,
                            content = "**回答**",
                            contextType = AI_CHAT_CONTEXT_MANUAL,
                        ),
                    ),
                isSending = true,
            )

        assertTrue(html.contains("""class="ry-ai-chat-message user""""))
        assertTrue(html.contains("""class="ry-ai-chat-message assistant""""))
        assertTrue(html.contains("<strong>回答</strong>"))
        assertTrue(html.contains("""class="ry-ai-chat-typing""""))
    }
}
