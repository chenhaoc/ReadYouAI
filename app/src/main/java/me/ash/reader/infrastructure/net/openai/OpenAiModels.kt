package me.ash.reader.infrastructure.net.openai

import com.google.gson.annotations.SerializedName

data class ChatMessage(
    @SerializedName("role")
    val role: String,
    @SerializedName("content")
    val content: String
)

data class ChatCompletionRequest(
    @SerializedName("model")
    val model: String,
    @SerializedName("messages")
    val messages: List<ChatMessage>,
    @SerializedName("temperature")
    val temperature: Double = 0.7,
    @SerializedName("max_tokens")
    val maxTokens: Int? = null,
    @SerializedName("thinking")
    val thinking: ChatThinkingConfig? = null,
)

data class ChatThinkingConfig(
    @SerializedName("type")
    val type: String,
)

data class ChatCompletionResponse(
    @SerializedName("choices")
    val choices: List<Choice>,
    @SerializedName("usage")
    val usage: Usage? = null
)

data class Choice(
    @SerializedName("message")
    val message: ChatMessage,
    @SerializedName("finish_reason")
    val finishReason: String? = null
)

data class Usage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int,
    @SerializedName("completion_tokens")
    val completionTokens: Int,
    @SerializedName("total_tokens")
    val totalTokens: Int
)

data class ModelsResponse(
    @SerializedName("object")
    val `object`: String,
    @SerializedName("data")
    val data: List<Model>
)

data class Model(
    @SerializedName("id")
    val id: String,
    @SerializedName("object")
    val `object`: String = "model",
    @SerializedName("created")
    val created: Long? = null,
    @SerializedName("owned_by")
    val ownedBy: String? = null
)

data class OpenAiResponsesRequest(
    @SerializedName("model")
    val model: String,
    @SerializedName("instructions")
    val instructions: String,
    @SerializedName("input")
    val input: List<ResponseInputMessage>,
    @SerializedName("tools")
    val tools: List<ResponseTool> = emptyList(),
    @SerializedName("tool_choice")
    val toolChoice: String? = null,
)

data class ResponseInputMessage(
    @SerializedName("role")
    val role: String,
    @SerializedName("content")
    val content: String,
)

data class ResponseTool(
    @SerializedName("type")
    val type: String,
    @SerializedName("search_context_size")
    val searchContextSize: String? = null,
)

data class OpenAiResponsesResponse(
    @SerializedName("output_text")
    val outputText: String? = null,
    @SerializedName("output")
    val output: List<ResponseOutputItem> = emptyList(),
    @SerializedName("status")
    val status: String? = null,
    @SerializedName("error")
    val error: OpenAiResponseError? = null,
    @SerializedName("incomplete_details")
    val incompleteDetails: OpenAiResponseIncompleteDetails? = null,
)

data class ResponseOutputItem(
    @SerializedName("type")
    val type: String? = null,
    @SerializedName("role")
    val role: String? = null,
    @SerializedName("content")
    val content: List<ResponseOutputContent> = emptyList(),
)

data class ResponseOutputContent(
    @SerializedName("type")
    val type: String? = null,
    @SerializedName("text")
    val text: String? = null,
    @SerializedName("annotations")
    val annotations: List<ResponseOutputAnnotation> = emptyList(),
)

data class ResponseOutputAnnotation(
    @SerializedName("type")
    val type: String? = null,
    @SerializedName("url")
    val url: String? = null,
    @SerializedName("title")
    val title: String? = null,
    @SerializedName("start_index")
    val startIndex: Int? = null,
    @SerializedName("end_index")
    val endIndex: Int? = null,
)

data class OpenAiResponseError(
    @SerializedName("message")
    val message: String? = null,
    @SerializedName("code")
    val code: String? = null,
)

data class OpenAiResponseIncompleteDetails(
    @SerializedName("reason")
    val reason: String? = null,
)
