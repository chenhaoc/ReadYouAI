package me.ash.reader.ui.page.home.reading

const val DEFAULT_AI_TRANSLATION_PROMPT =
    "请将内容翻译为简体中文，保持准确、自然、完整，尽量保留原意、语气和专有名词。"

fun resolveAiTranslationPrompt(prompt: String): String =
    prompt.ifBlank { DEFAULT_AI_TRANSLATION_PROMPT }
