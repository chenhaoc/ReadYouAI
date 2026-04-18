package me.ash.reader.domain.repository

import javax.inject.Inject
import javax.inject.Singleton
import me.ash.reader.infrastructure.net.ApiResult
import me.ash.reader.infrastructure.net.openai.ChatCompletionRequest
import me.ash.reader.infrastructure.net.openai.ChatMessage
import me.ash.reader.infrastructure.net.openai.OpenAiApiService

@Singleton
class AiChatRepository @Inject constructor() {

    suspend fun requestReply(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        articleTitle: String,
        feedName: String,
        articleLink: String?,
        articleContent: String,
        includeFullContent: Boolean,
        selectedSnippet: String?,
        history: List<me.ash.reader.domain.model.ai.AiChatMessage>,
        userQuestion: String,
    ): ApiResult<String> {
        return try {
            val service = OpenAiApiService.getInstance(baseUrl, apiKey)
            val messages =
                buildRequestMessages(
                    prompt = prompt,
                    articleTitle = articleTitle,
                    feedName = feedName,
                    articleLink = articleLink,
                    articleContent = articleContent,
                    includeFullContent = includeFullContent,
                    selectedSnippet = selectedSnippet,
                    history = history,
                    userQuestion = userQuestion,
                )
            val request = ChatCompletionRequest(
                model = model,
                messages = messages,
                temperature = 0.4,
                maxTokens = 2000,
            )
            val response = service.createChatCompletion(request)
            if (response.isSuccessful && response.body() != null) {
                val choices = response.body()!!.choices
                if (choices.isNotEmpty()) {
                    ApiResult.Success(choices.first().message.content)
                } else {
                    ApiResult.BizError(Exception("No choices returned from API"))
                }
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                ApiResult.BizError(Exception(errorMsg))
            }
        } catch (error: Exception) {
            ApiResult.NetworkError(error)
        }
    }

    internal fun buildRequestMessages(
        prompt: String,
        articleTitle: String,
        feedName: String,
        articleLink: String?,
        articleContent: String,
        includeFullContent: Boolean,
        selectedSnippet: String?,
        history: List<me.ash.reader.domain.model.ai.AiChatMessage>,
        userQuestion: String,
    ): List<ChatMessage> =
        buildList {
            add(
                ChatMessage(
                    role = "system",
                    content =
                        buildSystemPrompt(
                            prompt = prompt,
                            hasSelectedSnippet = !selectedSnippet.isNullOrBlank(),
                            includeFullContent = includeFullContent,
                        ),
                )
            )
            add(
                ChatMessage(
                    role = "user",
                    content = buildContextMessage(
                        articleTitle = articleTitle,
                        feedName = feedName,
                        articleLink = articleLink,
                        articleContent = articleContent,
                        includeFullContent = includeFullContent,
                        selectedSnippet = selectedSnippet,
                    ),
                )
            )
            history.takeLast(8).forEach { message ->
                add(ChatMessage(role = message.role, content = message.content))
            }
            add(ChatMessage(role = "user", content = userQuestion))
        }

    internal fun buildSystemPrompt(
        prompt: String,
        hasSelectedSnippet: Boolean,
        includeFullContent: Boolean,
    ): String =
        buildString {
            appendLine(prompt)
            appendLine()
            appendLine("补充要求：")
            appendLine("- 用简体中文回答")
            appendLine("- 优先直接回答用户当前问题")
            if (hasSelectedSnippet) {
                appendLine("- 如果提供了用户当前选中的内容，优先围绕选中内容回答")
            }
            if (includeFullContent) {
                appendLine("- 如果提供了文章全文，可将全文作为背景参考，但不要偏离当前问题")
            }
            appendLine("- 不确定时明确说明，不要编造")
        }.trim()

    private fun buildContextMessage(
        articleTitle: String,
        feedName: String,
        articleLink: String?,
        articleContent: String,
        includeFullContent: Boolean,
        selectedSnippet: String?,
    ): String = buildString {
        appendLine("[Article]")
        appendLine("Title: $articleTitle")
        appendLine("Source: $feedName")
        if (!articleLink.isNullOrBlank()) {
            appendLine("Link: $articleLink")
        }
        if (!selectedSnippet.isNullOrBlank()) {
            appendLine()
            appendLine("[Selected Text]")
            appendLine(selectedSnippet)
        }
        if (includeFullContent) {
            appendLine()
            appendLine("[Full Content]")
            appendLine(articleContent)
        }
    }
}
