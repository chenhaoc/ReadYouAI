package me.ash.reader.infrastructure.net.openai

import com.google.gson.Gson
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiApiServiceTest {

    @Test
    fun normalizeBaseUrlAddsTrailingSlash() {
        assertEquals(
            "https://api.openai.com/v1/",
            OpenAiApiService.normalizeBaseUrl("https://api.openai.com/v1"),
        )
    }

    @Test
    fun normalizeBaseUrlTrimsWhitespaceAndDuplicateTrailingSlashes() {
        assertEquals(
            "https://example.com/openai/",
            OpenAiApiService.normalizeBaseUrl("  https://example.com/openai///  "),
        )
    }

    @Test
    fun normalizeBaseUrlFallsBackToDefaultWhenBlank() {
        assertEquals(
            "https://api.openai.com/v1/",
            OpenAiApiService.normalizeBaseUrl("   "),
        )
    }

    @Test
    fun openAiRequestModels_serializeOpenAiSnakeCaseFields() {
        val chatJson =
            Gson().toJson(
                ChatCompletionRequest(
                    model = "gpt-5.4-mini",
                    messages = listOf(ChatMessage(role = "user", content = "Hi")),
                    maxTokens = 8,
                )
            )
        val responsesJson =
            Gson().toJson(
                OpenAiResponsesRequest(
                    model = "gpt-5.4-mini",
                    instructions = "Answer directly.",
                    input = listOf(ResponseInputMessage(role = "user", content = "Hi")),
                    tools = listOf(ResponseTool(type = "web_search", searchContextSize = "medium")),
                    toolChoice = "auto",
                )
            )

        assertTrue(chatJson.contains("\"max_tokens\":8"))
        assertFalse(chatJson.contains("maxTokens"))
        assertTrue(responsesJson.contains("\"tool_choice\":\"auto\""))
        assertTrue(responsesJson.contains("\"search_context_size\":\"medium\""))
    }
}
