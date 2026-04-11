package me.ash.reader.domain.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import me.ash.reader.ui.page.home.reading.TranslationSourceBlock

class ArticleTranslationPayloadTest {

    @Test
    fun decodesJsonCodeFencePayloadWhenAllIdsArePresent() {
        val result = ArticleTranslationPayloadCodec.decodeTranslatedBlocks(
            rawContent =
                """
                ```json
                [
                  {"id":"paragraph_1","translatedText":"第一段"},
                  {"id":"paragraph_2","translatedText":"第二段"}
                ]
                ```
                """.trimIndent(),
            expectedIds = setOf("paragraph_1", "paragraph_2"),
        )

        assertTrue(result is me.ash.reader.infrastructure.net.ApiResult.Success)
        result as me.ash.reader.infrastructure.net.ApiResult.Success
        assertEquals("第一段", result.data.first().translatedText)
    }

    @Test
    fun rejectsPayloadWhenIdsAreMissing() {
        val result = ArticleTranslationPayloadCodec.decodeTranslatedBlocks(
            rawContent = """[{"id":"paragraph_1","translatedText":"第一段"}]""",
            expectedIds = setOf("paragraph_1", "paragraph_2"),
        )

        assertTrue(result is me.ash.reader.infrastructure.net.ApiResult.BizError)
    }

    @Test
    fun chunksLongSourceBlocksIntoMultipleRequests() {
        val blocks =
            listOf(
                TranslationSourceBlock("paragraph_1", "paragraph", "a".repeat(700)),
                TranslationSourceBlock("paragraph_2", "paragraph", "b".repeat(700)),
                TranslationSourceBlock("paragraph_3", "paragraph", "c".repeat(700)),
            )

        val chunks =
            TranslationRequestChunker.chunk(
                blocks,
                maxPayloadChars = 10000,
                maxEstimatedOutputTokens = 500,
            )

        assertEquals(3, chunks.sumOf { it.size })
        assertTrue(chunks.size > 1)
        assertEquals(listOf("paragraph_1"), chunks.first().map { it.id })
    }
}
