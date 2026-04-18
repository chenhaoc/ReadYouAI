package me.ash.reader.ui.page.home.reading

private val DEFAULT_AI_CHAT_PROMPT =
    """
    你是一个中文新闻阅读助手。

    回答规则：
    - 如果提供了“用户当前选中的内容”，优先回答该内容本身。
    - 如果同时提供了文章全文，可将全文作为背景参考以提高准确性，但不要主动偏离用户当前问题。
    - 如果没有提供选中内容，则围绕整篇文章回答。
    - 不确定时明确说明，不要编造事实。
    - 回答尽量直接、清楚、便于理解。
    """.trimIndent()

const val AI_CHAT_ROLE_USER = "user"
const val AI_CHAT_ROLE_ASSISTANT = "assistant"

const val AI_CHAT_CONTEXT_MANUAL = "manual"
const val AI_CHAT_CONTEXT_EXPLAIN_ARTICLE = "article"
const val AI_CHAT_CONTEXT_EXPLAIN_SELECTION = "selection"
const val AI_CHAT_CONTEXT_BACKGROUND = "background"
const val AI_CHAT_CONTEXT_INTRO = "intro"

enum class AiChatQuickAction {
    ExplainArticle,
    ExplainSelection,
    GiveBackground,
    Introduce,
}

fun resolveAiChatPrompt(prompt: String): String =
    prompt.ifBlank { DEFAULT_AI_CHAT_PROMPT }

fun buildAiChatQuickQuestion(
    action: AiChatQuickAction,
    hasSelection: Boolean,
): String =
    when (action) {
        AiChatQuickAction.ExplainArticle -> "请解释这篇文章在讲什么，并概括重点。"
        AiChatQuickAction.ExplainSelection -> "请解释用户当前选中的这段内容是什么意思。"
        AiChatQuickAction.GiveBackground ->
            if (hasSelection) {
                "请补充理解这段内容所需要的背景知识。"
            } else {
                "请补充理解这篇文章所需要的背景知识。"
            }

        AiChatQuickAction.Introduce ->
            if (hasSelection) {
                "请用简单易懂的话介绍这段内容涉及的对象。"
            } else {
                "请用简单易懂的话介绍这篇文章的核心主题。"
            }
    }

fun contextTypeForQuickAction(action: AiChatQuickAction): String =
    when (action) {
        AiChatQuickAction.ExplainArticle -> AI_CHAT_CONTEXT_EXPLAIN_ARTICLE
        AiChatQuickAction.ExplainSelection -> AI_CHAT_CONTEXT_EXPLAIN_SELECTION
        AiChatQuickAction.GiveBackground -> AI_CHAT_CONTEXT_BACKGROUND
        AiChatQuickAction.Introduce -> AI_CHAT_CONTEXT_INTRO
    }
