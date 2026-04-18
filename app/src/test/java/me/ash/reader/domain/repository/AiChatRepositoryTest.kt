package me.ash.reader.domain.repository

import java.util.Date
import me.ash.reader.domain.model.ai.AiChatMessage
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
}
