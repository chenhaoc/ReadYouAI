package me.ash.reader.ui.page.home.reading

private val DEFAULT_AI_CHAT_PROMPT =
    "请基于提供的内容，用简体中文直接、清楚地回答问题；优先回答当前问题本身，不确定时请明确说明。"

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
