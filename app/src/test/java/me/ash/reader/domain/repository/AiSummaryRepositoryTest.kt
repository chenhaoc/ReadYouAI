package me.ash.reader.domain.repository

import org.junit.Assert.assertTrue
import org.junit.Test

class AiSummaryRepositoryTest {

    private val repository = AiSummaryRepository()

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
}
