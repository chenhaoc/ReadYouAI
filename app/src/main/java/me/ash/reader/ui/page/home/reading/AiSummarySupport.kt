package me.ash.reader.ui.page.home.reading

import me.ash.reader.ui.page.adaptive.ReadingUiState

const val DEFAULT_AI_SUMMARIZATION_PROMPT =
    "You are a summarizer. Generate a concise summary of the given text. Output ONLY the summary in Chinese, nothing else."

fun resolveAiSummarizationPrompt(prompt: String): String =
    prompt.ifBlank { DEFAULT_AI_SUMMARIZATION_PROMPT }

fun shouldAutoSummarize(feedAutoSummary: Boolean, state: ReadingUiState): Boolean =
    feedAutoSummary && state.shouldAutoGenerateAiSummary
