package me.ash.reader.domain.repository

import javax.inject.Inject
import javax.inject.Singleton
import me.ash.reader.infrastructure.net.ApiResult
import me.ash.reader.infrastructure.net.openai.ChatCompletionRequest
import me.ash.reader.infrastructure.net.openai.ChatMessage
import me.ash.reader.infrastructure.net.openai.OpenAiApiService
import me.ash.reader.ui.page.home.reading.TranslationSourceBlock

@Singleton
class AiTranslationRepository @Inject constructor() {

    suspend fun translateBlocks(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        sourceBlocks: List<TranslationSourceBlock>,
    ): ApiResult<List<TranslatedArticleBlock>> {
        return try {
            val service = OpenAiApiService.getInstance(baseUrl, apiKey)
            val translatedBlocks = mutableListOf<TranslatedArticleBlock>()
            val chunks = TranslationRequestChunker.chunk(sourceBlocks)

            for (chunk in chunks) {
                val payloadJson = ArticleTranslationPayloadCodec.encodeSourceBlocks(chunk)
                val request = ChatCompletionRequest(
                    model = model,
                    messages = listOf(
                        ChatMessage(
                            role = "user",
                            content = "$prompt\n\nInput JSON:\n$payloadJson",
                        )
                    ),
                    temperature = 0.2,
                    maxTokens = 1200,
                )
                val response = service.createChatCompletion(request)
                if (!response.isSuccessful || response.body() == null) {
                    val errorMsg = response.errorBody()?.string() ?: "Unknown error"
                    return ApiResult.BizError(Exception(errorMsg))
                }
                val choices = response.body()!!.choices
                if (choices.isEmpty()) {
                    return ApiResult.BizError(Exception("No choices returned from API"))
                }
                when (
                    val parsedResult =
                        ArticleTranslationPayloadCodec.decodeTranslatedBlocks(
                            rawContent = choices.first().message.content,
                            expectedIds = chunk.map { it.id }.toSet(),
                        )
                ) {
                    is ApiResult.Success -> translatedBlocks += parsedResult.data
                    is ApiResult.BizError -> return parsedResult
                    is ApiResult.NetworkError -> return parsedResult
                    is ApiResult.UnknownError -> return parsedResult
                }
            }

            ApiResult.Success(translatedBlocks)
        } catch (error: Exception) {
            ApiResult.NetworkError(error)
        }
    }
}
