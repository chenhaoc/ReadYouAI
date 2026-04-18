package me.ash.reader.domain.repository

import me.ash.reader.infrastructure.net.ApiResult
import me.ash.reader.infrastructure.net.openai.OpenAiApiService
import me.ash.reader.infrastructure.net.openai.ChatCompletionRequest
import me.ash.reader.infrastructure.net.openai.ChatMessage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiSummaryRepository @Inject constructor() {

    suspend fun fetchAvailableModels(
        baseUrl: String,
        apiKey: String
    ): ApiResult<List<String>> {
        return try {
            val service = OpenAiApiService.getInstance(baseUrl, apiKey)
            val response = service.getModels()

            if (response.isSuccessful && response.body() != null) {
                val modelIds = response.body()!!.data.map { it.id }
                ApiResult.Success(modelIds)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                ApiResult.BizError(Exception(errorMsg))
            }
        } catch (e: Exception) {
            ApiResult.NetworkError(e)
        }
    }

    suspend fun summarizeArticle(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        articleContent: String
    ): ApiResult<String> {
        return try {
            val service = OpenAiApiService.getInstance(baseUrl, apiKey)
            val messages = buildSummaryMessages(prompt = prompt, articleContent = articleContent)

            val request = ChatCompletionRequest(
                model = model,
                messages = messages,
                temperature = 0.7,
                maxTokens = 2000
            )

            val response = service.createChatCompletion(request)

            if (response.isSuccessful && response.body() != null) {
                val choices = response.body()!!.choices
                if (choices.isNotEmpty()) {
                    val summary = choices[0].message.content
                    ApiResult.Success(summary)
                } else {
                    ApiResult.BizError(Exception("No choices returned from API"))
                }
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                ApiResult.BizError(Exception(errorMsg))
            }
        } catch (e: Exception) {
            ApiResult.NetworkError(e)
        }
    }

    internal fun buildSummaryMessages(
        prompt: String,
        articleContent: String,
    ): List<ChatMessage> =
        listOf(
            ChatMessage(
                role = "system",
                content =
                    buildString {
                        appendLine(prompt)
                        appendLine()
                        appendLine("补充要求：")
                        appendLine("- 只输出摘要正文")
                        appendLine("- 不要添加标题、前言、结尾或额外说明")
                        appendLine("- 按结构化短段落输出，最多 4 段")
                        appendLine("- 每段只写 1 到 2 句，没有对应信息就省略")
                        appendLine("- 优先覆盖：发生了什么、关键事实、为什么值得关注")
                        appendLine("- 如果原文信息不足，只基于已有内容总结，不要编造")
                    }.trim(),
            ),
            ChatMessage(role = "user", content = articleContent),
        )
}
