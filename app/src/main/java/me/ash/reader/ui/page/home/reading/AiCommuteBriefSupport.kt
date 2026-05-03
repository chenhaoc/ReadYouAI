package me.ash.reader.ui.page.home.reading

val DEFAULT_AI_COMMUTE_BRIEF_RECOMMENDATION_PROMPT =
    """
    请从候选文章中挑选更值得在通勤中收听的文章。
    你是科技新闻通勤简报编辑。

    选择标准：
    - 优先选择信息量高、影响范围大、适合科技新闻收听的内容
    - 避免重复主题或同一事件的轻微更新占满列表
    - 保持一定来源多样性
    - 降低碎片新闻、纯转述、标题党优先级
    """.trimIndent()

fun resolveAiCommuteBriefRecommendationPrompt(prompt: String): String =
    prompt.ifBlank { DEFAULT_AI_COMMUTE_BRIEF_RECOMMENDATION_PROMPT }
