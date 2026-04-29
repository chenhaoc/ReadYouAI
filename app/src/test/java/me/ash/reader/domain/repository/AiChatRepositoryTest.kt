package me.ash.reader.domain.repository

import java.util.Date
import me.ash.reader.domain.model.ai.AiChatMessage
import me.ash.reader.infrastructure.net.openai.OpenAiResponsesResponse
import me.ash.reader.infrastructure.net.openai.ResponseOutputAnnotation
import me.ash.reader.infrastructure.net.openai.ResponseOutputContent
import me.ash.reader.infrastructure.net.openai.ResponseOutputItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatRepositoryTest {

    private val repository = AiChatRepository()

    @Test
    fun buildRequestMessages_appendsCurrentQuestionOnce_afterPriorHistory() {
        val messages =
            repository.buildRequestMessages(
                prompt = "system prompt",
                articleTitle = "Title",
                feedName = "Feed",
                articleLink = "https://example.com",
                articleContent = "Article content",
                includeFullContent = true,
                selectedSnippet = "Selected text",
                history =
                    listOf(
                        AiChatMessage(
                            id = 1,
                            articleId = "article-1",
                            role = "user",
                            content = "first question",
                            contextType = "manual",
                            createdAt = Date(1),
                        ),
                        AiChatMessage(
                            id = 2,
                            articleId = "article-1",
                            role = "assistant",
                            content = "first answer",
                            contextType = "manual",
                            createdAt = Date(2),
                        ),
                    ),
                userQuestion = "current question",
            )

        assertEquals(listOf("system", "user", "user", "assistant", "user"), messages.map { it.role })
        assertEquals("current question", messages.last().content)
        assertEquals(1, messages.count { it.role == "user" && it.content == "current question" })
    }

    @Test
    fun buildRequestMessages_limitsHistoryToMostRecentEightMessages() {
        val history =
            (1..10).map { index ->
                AiChatMessage(
                    id = index.toLong(),
                    articleId = "article-1",
                    role = if (index % 2 == 0) "assistant" else "user",
                    content = "message-$index",
                    contextType = "manual",
                    createdAt = Date(index.toLong()),
                )
            }

        val messages =
            repository.buildRequestMessages(
                prompt = "system prompt",
                articleTitle = "Title",
                feedName = "Feed",
                articleLink = null,
                articleContent = "Article content",
                includeFullContent = false,
                selectedSnippet = null,
                history = history,
                userQuestion = "current question",
            )

        val historyContents = messages.drop(2).dropLast(1).map { it.content }
        assertEquals((3..10).map { "message-$it" }, historyContents)
        assertTrue(messages[1].content.contains("[Article]"))
    }

    @Test
    fun buildSystemPrompt_keepsSelectionAndFullTextRulesInternal() {
        val prompt =
            repository.buildSystemPrompt(
                prompt = "请基于提供的内容，用简体中文直接、清楚地回答问题；优先回答当前问题本身，不确定时请明确说明。",
                hasSelectedSnippet = true,
                includeFullContent = true,
            )

        assertTrue(prompt.contains("优先围绕选中内容回答"))
        assertTrue(prompt.contains("可将全文作为背景参考"))
        assertTrue(prompt.contains("不要编造"))
    }

    @Test
    fun buildResponsesRequest_enablesWebSearchToolAndKeepsChatContext() {
        val request =
            repository.buildResponsesRequest(
                model = "gpt-5.4-mini",
                prompt = "system prompt",
                articleTitle = "Title",
                feedName = "Feed",
                articleLink = "https://example.com",
                articleContent = "Article content",
                includeFullContent = true,
                selectedSnippet = null,
                history =
                    listOf(
                        AiChatMessage(
                            id = 1,
                            articleId = "article-1",
                            role = "assistant",
                            content = "prior answer",
                            contextType = "manual",
                            createdAt = Date(1),
                        )
                    ),
                userQuestion = "current question",
            )

        assertEquals("gpt-5.4-mini", request.model)
        assertEquals("web_search", request.tools.single().type)
        assertEquals("auto", request.toolChoice)
        assertTrue(request.instructions.contains("联网搜索工具"))
        assertEquals(listOf("user", "assistant", "user"), request.input.map { it.role })
        assertEquals("current question", request.input.last().content)
    }

    @Test
    fun extractResponsesReply_appendsUrlCitationSources() {
        val response =
            OpenAiResponsesResponse(
                output =
                    listOf(
                        ResponseOutputItem(
                            type = "message",
                            content =
                                listOf(
                                    ResponseOutputContent(
                                        type = "output_text",
                                        text = "这是搜索后的回答。",
                                        annotations =
                                            listOf(
                                                ResponseOutputAnnotation(
                                                    type = "url_citation",
                                                    url = "https://example.com/report",
                                                    title = "Example report",
                                                )
                                            ),
                                    )
                                ),
                        )
                    )
            )

        val reply = repository.extractResponsesReply(response)

        assertTrue(reply.contains("这是搜索后的回答。"))
        assertTrue(reply.contains("来源："))
        assertTrue(reply.contains("[Example report](https://example.com/report)"))
    }

    @Test
    fun parseResponsesBody_readsFinalResponseFromSseStream() {
        val rawBody =
            """
            event: response.created
            data: {"type":"response.created","response":{"status":"in_progress","output":[]}}

            event: response.completed
            data: {"type":"response.completed","response":{"status":"completed","output":[{"type":"web_search_call"},{"type":"message","content":[{"type":"output_text","text":"搜索后的回答"}]}]}}
            """.trimIndent()

        val response = repository.parseResponsesBody(rawBody)

        assertEquals("completed", response.status)
        assertEquals(listOf("web_search_call", "message"), response.output.map { it.type })
        assertEquals("搜索后的回答", repository.extractResponsesReply(response))
    }
}
