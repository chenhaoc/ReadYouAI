package me.ash.reader.domain.repository

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import me.ash.reader.infrastructure.net.ApiResult
import me.ash.reader.ui.page.home.reading.TranslationSourceBlock

data class TranslatedArticleBlock(
    val id: String,
    val translatedText: String,
)

object ArticleTranslationPayloadCodec {
    private val gson = Gson()

    fun encodeSourceBlocks(sourceBlocks: List<TranslationSourceBlock>): String {
        return gson.toJson(sourceBlocks)
    }

    fun decodeTranslatedBlocks(
        rawContent: String,
        expectedIds: Set<String>,
    ): ApiResult<List<TranslatedArticleBlock>> {
        return try {
            val cleanedContent = rawContent.stripCodeFence()
            val listType = object : TypeToken<List<TranslatedArticleBlock>>() {}.type
            val translatedBlocks: List<TranslatedArticleBlock> =
                gson.fromJson(cleanedContent, listType)
                    ?: return ApiResult.BizError(Exception("No translation payload returned"))

            val actualIds = translatedBlocks.map { it.id }.toSet()
            if (translatedBlocks.size != expectedIds.size || actualIds != expectedIds) {
                return ApiResult.BizError(Exception("Translation payload did not cover all source ids"))
            }

            if (translatedBlocks.any { it.translatedText.isBlank() }) {
                return ApiResult.BizError(Exception("Translation payload contained empty translatedText"))
            }

            ApiResult.Success(translatedBlocks)
        } catch (error: JsonSyntaxException) {
            ApiResult.BizError(Exception("Translation payload was not valid JSON", error))
        }
    }

    fun decodeStoredBlocks(rawContent: String?): List<TranslatedArticleBlock> {
        if (rawContent.isNullOrBlank()) return emptyList()
        return try {
            val listType = object : TypeToken<List<TranslatedArticleBlock>>() {}.type
            gson.fromJson<List<TranslatedArticleBlock>>(rawContent, listType).orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun String.stripCodeFence(): String {
        val trimmed = trim()
        if (!trimmed.startsWith("```")) return trimmed
        return trimmed
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }
}

object TranslationRequestChunker {
    fun chunk(
        sourceBlocks: List<TranslationSourceBlock>,
        maxPayloadChars: Int = 4000,
        maxEstimatedOutputTokens: Int = 450,
    ): List<List<TranslationSourceBlock>> {
        if (sourceBlocks.isEmpty()) return emptyList()

        val chunks = mutableListOf<MutableList<TranslationSourceBlock>>()
        var currentChunk = mutableListOf<TranslationSourceBlock>()

        sourceBlocks.forEach { block ->
            if (currentChunk.isEmpty()) {
                currentChunk.add(block)
                return@forEach
            }

            val candidate = currentChunk + block
            val candidateSize = ArticleTranslationPayloadCodec.encodeSourceBlocks(candidate).length
            val candidateTokens = candidate.sumOf { estimateOutputTokens(it.text) }
            if (candidateSize > maxPayloadChars || candidateTokens > maxEstimatedOutputTokens) {
                chunks += currentChunk
                currentChunk = mutableListOf(block)
            } else {
                currentChunk.add(block)
            }
        }

        if (currentChunk.isNotEmpty()) {
            chunks += currentChunk
        }
        return chunks
    }

    internal fun estimateOutputTokens(text: String): Int {
        val normalizedLength = text.trim().length.coerceAtLeast(1)
        return (normalizedLength / 2.5).toInt().coerceAtLeast(1)
    }
}
