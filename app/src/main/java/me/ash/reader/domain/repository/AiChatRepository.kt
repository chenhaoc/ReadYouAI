package me.ash.reader.domain.repository

import com.google.gson.Gson
import com.google.gson.JsonParser
import javax.inject.Inject
import javax.inject.Singleton
import me.ash.reader.infrastructure.net.ApiResult
import me.ash.reader.infrastructure.net.openai.ChatCompletionRequest
import me.ash.reader.infrastructure.net.openai.ChatMessage
import me.ash.reader.infrastructure.net.openai.OpenAiApiService
import me.ash.reader.infrastructure.net.openai.OpenAiResponseError
import me.ash.reader.infrastructure.net.openai.OpenAiResponsesRequest
import me.ash.reader.infrastructure.net.openai.OpenAiResponsesResponse
import me.ash.reader.infrastructure.net.openai.ResponseInputMessage
import me.ash.reader.infrastructure.net.openai.ResponseOutputAnnotation
import me.ash.reader.infrastructure.net.openai.ResponseTool

@Singleton
class AiChatRepository @Inject constructor() {

    suspend fun testWebSearch(
        baseUrl: String,
        apiKey: String,
        model: String,
    ): ApiResult<Unit> {
        return try {
            val service = OpenAiApiService.getInstance(
                baseUrl = baseUrl,
                apiKey = apiKey,
                timeoutSeconds = AI_WEB_SEARCH_TEST_TIMEOUT_SECONDS,
                callTimeoutSeconds = AI_WEB_SEARCH_TEST_TIMEOUT_SECONDS,
            )
            val response = service.createRawResponse(buildWebSearchTestRequest(model))
            if (response.isSuccessful && response.body() != null) {
                val rawBody = response.body()!!.string()
                val body = parseResponsesBody(rawBody)
                val reply = extractResponsesReply(body)
                if (reply.isNotBlank()) {
                    ApiResult.Success(Unit)
                } else {
                    val errorMsg =
                        body.error?.message
                            ?: body.incompleteDetails?.reason?.let { "Response incomplete: $it" }
                            ?: "No output text returned from Responses API"
                    ApiResult.BizError(Exception(errorMsg))
                }
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                ApiResult.BizError(Exception(errorMsg))
            }
        } catch (error: Exception) {
            ApiResult.NetworkError(error)
        }
    }

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
        val responsesResult =
            requestReplyWithResponses(
                baseUrl = baseUrl,
                apiKey = apiKey,
                model = model,
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
        if (responsesResult is ApiResult.Success) {
            return responsesResult
        }

        return requestReplyWithChatCompletions(
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model,
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
    }

    private suspend fun requestReplyWithResponses(
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
            val service = OpenAiApiService.getInstance(
                baseUrl = baseUrl,
                apiKey = apiKey,
                timeoutSeconds = AI_CHAT_RESPONSES_TIMEOUT_SECONDS,
                callTimeoutSeconds = AI_CHAT_RESPONSES_TIMEOUT_SECONDS,
            )
            val request =
                buildResponsesRequest(
                    model = model,
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
            val response =
                service.createRawResponse(request)
            if (response.isSuccessful && response.body() != null) {
                val rawBody = response.body()!!.string()
                val body = parseResponsesBody(rawBody)
                val reply = extractResponsesReply(body)
                if (reply.isNotBlank()) {
                    ApiResult.Success(reply)
                } else {
                    val errorMsg =
                        body.error?.message
                            ?: body.incompleteDetails?.reason?.let { "Response incomplete: $it" }
                            ?: "No output text returned from Responses API"
                    ApiResult.BizError(Exception(errorMsg))
                }
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                ApiResult.BizError(Exception(errorMsg))
            }
        } catch (error: Exception) {
            ApiResult.NetworkError(error)
        }
    }

    private suspend fun requestReplyWithChatCompletions(
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

    internal fun buildResponsesRequest(
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
    ): OpenAiResponsesRequest =
        OpenAiResponsesRequest(
            model = model,
            instructions =
                buildResponsesInstructions(
                    prompt = prompt,
                    hasSelectedSnippet = !selectedSnippet.isNullOrBlank(),
                    includeFullContent = includeFullContent,
                ),
            input =
                buildResponsesInputMessages(
                    articleTitle = articleTitle,
                    feedName = feedName,
                    articleLink = articleLink,
                    articleContent = articleContent,
                    includeFullContent = includeFullContent,
                    selectedSnippet = selectedSnippet,
                    history = history,
                    userQuestion = userQuestion,
                ),
            tools = listOf(ResponseTool(type = WEB_SEARCH_TOOL_TYPE)),
            toolChoice = "auto",
        )

    internal fun buildWebSearchTestRequest(model: String): OpenAiResponsesRequest =
        buildResponsesRequest(
            model = model,
            prompt = "请用简体中文直接回答。",
            articleTitle = "联网搜索测试",
            feedName = "ReadYouAI",
            articleLink = null,
            articleContent = "",
            includeFullContent = false,
            selectedSnippet = null,
            history = emptyList(),
            userQuestion = "请联网搜索“今天北京天气”，并用一句话回答搜索是否成功。",
        )

    internal fun extractResponsesReply(response: OpenAiResponsesResponse): String {
        val directText = response.outputText.orEmpty().trim()
        val outputText =
            directText.ifBlank {
                response.output
                    .flatMap { it.content }
                    .filter { it.type == "output_text" }
                    .mapNotNull { it.text?.trim()?.takeIf { text -> text.isNotBlank() } }
                    .joinToString("\n\n")
            }.trim()
        if (outputText.isBlank()) {
            return ""
        }

        val sources =
            response.output
                .flatMap { it.content }
                .flatMap { it.annotations }
                .filter { it.type == "url_citation" && !it.url.isNullOrBlank() }
                .distinctBy { it.url }
                .take(MAX_WEB_SEARCH_SOURCES)

        return appendWebSearchSources(outputText, sources)
    }

    internal fun parseResponsesBody(rawBody: String): OpenAiResponsesResponse {
        val trimmed = rawBody.trim()
        if (!trimmed.startsWith("event:") && !trimmed.startsWith("data:")) {
            return gson.fromJson(trimmed, OpenAiResponsesResponse::class.java)
        }

        var finalResponse: OpenAiResponsesResponse? = null
        trimmed.lineSequence()
            .filter { it.startsWith("data:") }
            .map { it.removePrefix("data:").trim() }
            .filter { it.isNotBlank() && it != "[DONE]" }
            .forEach { data ->
                parseResponseEventData(data)?.let { parsed ->
                    if (parsed.output.isNotEmpty() || parsed.status == "completed") {
                        finalResponse = parsed
                    }
                }
            }
        return finalResponse ?: OpenAiResponsesResponse(
            error = OpenAiResponseError(
                message = "No completed response event returned from Responses API stream"
            )
        )
    }

    private fun parseResponseEventData(data: String): OpenAiResponsesResponse? =
        runCatching {
            val element = JsonParser.parseString(data)
            val responseElement =
                element.takeIf { it.isJsonObject }
                    ?.asJsonObject
                    ?.get("response")
                    ?: element
            gson.fromJson(responseElement, OpenAiResponsesResponse::class.java)
        }.getOrNull()

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

    private fun buildResponsesInstructions(
        prompt: String,
        hasSelectedSnippet: Boolean,
        includeFullContent: Boolean,
    ): String =
        buildString {
            append(
                buildSystemPrompt(
                    prompt = prompt,
                    hasSelectedSnippet = hasSelectedSnippet,
                    includeFullContent = includeFullContent,
                )
            )
            appendLine()
            appendLine("- 用户要求联网、搜索、查询最新信息或外部事实核对时，必须使用联网搜索工具")
            appendLine("- 如果无法完成联网搜索，明确说明无法联网验证，不要用普通回答伪装成搜索结果")
            appendLine("- 使用搜索结果时，优先引用可靠来源，并避免把搜索信息和文章原文混淆")
        }.trim()

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

    private fun buildResponsesInputMessages(
        articleTitle: String,
        feedName: String,
        articleLink: String?,
        articleContent: String,
        includeFullContent: Boolean,
        selectedSnippet: String?,
        history: List<me.ash.reader.domain.model.ai.AiChatMessage>,
        userQuestion: String,
    ): List<ResponseInputMessage> =
        buildList {
            add(
                ResponseInputMessage(
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
                add(ResponseInputMessage(role = message.role, content = message.content))
            }
            add(ResponseInputMessage(role = "user", content = userQuestion))
        }

    private fun appendWebSearchSources(
        text: String,
        sources: List<ResponseOutputAnnotation>,
    ): String {
        if (sources.isEmpty()) {
            return text
        }

        val sourceLines =
            sources.mapIndexed { index, source ->
                val url = source.url.orEmpty()
                val title = source.title?.takeIf { it.isNotBlank() } ?: url
                "${index + 1}. [${escapeMarkdownLinkLabel(title)}](${escapeMarkdownUrl(url)})"
            }
        return buildString {
            append(text.trim())
            appendLine()
            appendLine()
            appendLine("来源：")
            append(sourceLines.joinToString("\n"))
        }
    }

    private fun escapeMarkdownLinkLabel(text: String): String =
        text.replace("[", "\\[").replace("]", "\\]")

    private fun escapeMarkdownUrl(url: String): String =
        url.replace(" ", "%20").replace(")", "%29")

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

    private companion object {
        val gson = Gson()
        const val AI_CHAT_RESPONSES_TIMEOUT_SECONDS = 90L
        const val AI_WEB_SEARCH_TEST_TIMEOUT_SECONDS = 45L
        const val MAX_WEB_SEARCH_SOURCES = 8
        const val WEB_SEARCH_TOOL_TYPE = "web_search"
    }
}
