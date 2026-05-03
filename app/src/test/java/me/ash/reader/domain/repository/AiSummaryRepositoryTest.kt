package me.ash.reader.domain.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSummaryRepositoryTest {

    private val repository = AiSummaryRepository()

    @Test
    fun parseRecommendedArticleIds_readsPlainJson() {
        val ids = repository.parseRecommendedArticleIds("{\"articleIds\":[\"a1\",\"a2\"]}")

        assertEquals(listOf("a1", "a2"), ids)
    }

    @Test
    fun parseRecommendedArticleIds_readsFencedJson() {
        val ids =
            repository.parseRecommendedArticleIds(
                """
                ```json
                {"articleIds":["a1","a2"]}
                ```
                """.trimIndent(),
            )

        assertEquals(listOf("a1", "a2"), ids)
    }

    @Test
    fun buildCommuteBriefRecommendationMessages_containsCandidateAndJsonRules() {
        val messages =
            repository.buildCommuteBriefRecommendationMessages(
                prompt = "请从候选文章中挑选更值得收听的文章。",
                targetDurationMinutes = 30,
                candidates =
                    listOf(
                        CommuteBriefRecommendationCandidate(
                            articleId = "a1",
                            title = "重要科技新闻",
                            feedName = "Tech Feed",
                            publishedAt = "2026-04-24 08:12",
                            summary = "这是一条值得通勤收听的摘要。",
                            estimatedDurationMinutes = 3,
                        )
                    ),
            )

        assertTrue(messages.first().content.contains("请从候选文章中挑选更值得收听的文章。"))
        assertTrue(messages.first().content.contains("目标总时长约 30 分钟"))
        assertTrue(messages.first().content.contains("只输出 JSON"))
        assertTrue(messages.first().content.contains("articleIds"))
        assertTrue(messages.last().content.contains("id: a1"))
        assertTrue(messages.last().content.contains("重要科技新闻"))
    }

    @Test
    fun buildSummaryMessages_keepsStructuredLengthGuidanceInternal() {
        val messages =
            repository.buildSummaryMessages(
                prompt =
                    """
                    请用简体中文写一份适合快速阅读的结构化摘要，按 3 到 4 个短段落输出，每段聚焦一个重点，每段 1 到 2 句。

                    先说明发生了什么，再补充关键事实，然后说明为什么值得关注；如果还有必要，再补充后续变化、争议或不确定性。

                    要求表达清楚、信息密度高、避免重复，不要写成流水账。
                    """.trimIndent(),
                articleContent = "示例文章内容",
            )

        assertTrue(messages.first().content.contains("最多 4 段"))
        assertTrue(messages.first().content.contains("每段只写 1 到 2 句"))
        assertTrue(messages.first().content.contains("发生了什么、关键事实、为什么值得关注"))
    }

    @Test
    fun buildConnectionTestThinkingConfig_disablesThinkingForDeepSeekOnly() {
        assertEquals(
            "disabled",
            repository.buildConnectionTestThinkingConfig("https://api.deepseek.com")?.type,
        )
        assertEquals(
            "disabled",
            repository.buildConnectionTestThinkingConfig("https://api.deepseek.com/")?.type,
        )
        assertNull(repository.buildConnectionTestThinkingConfig("https://api.openai.com/v1"))
    }
}
