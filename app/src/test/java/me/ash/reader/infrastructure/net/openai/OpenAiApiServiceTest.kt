package me.ash.reader.infrastructure.net.openai

import org.junit.Assert.assertEquals
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
}
