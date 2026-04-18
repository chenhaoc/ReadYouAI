package me.ash.reader.domain.repository

import org.junit.Assert.assertTrue
import org.junit.Test

class AiTranslationRepositoryTest {

    private val repository = AiTranslationRepository()

    @Test
    fun buildTranslationMessages_keepsProtocolInstructionsHiddenInSystemPrompt() {
        val messages =
            repository.buildTranslationMessages(
                prompt = "请将内容翻译为简体中文，保持准确、自然、完整，尽量保留原意、语气和专有名词。",
                payloadJson = """[{"id":"p1","type":"paragraph","text":"Hello"}]""",
            )

        assertTrue(messages.first().content.contains("只返回 JSON"))
        assertTrue(messages.first().content.contains("translatedText"))
        assertTrue(messages.last().content.contains("翻译要求："))
        assertTrue(messages.last().content.contains("保持准确、自然、完整"))
    }
}
