package me.ash.reader.ui.page.home.reading

import me.ash.reader.domain.repository.ArticleTranslationPayloadCodec

const val TRANSLATION_TITLE_BLOCK_ID = "list_title"

fun resolveTranslatedTitle(translationBlocks: String?): String? {
    return ArticleTranslationPayloadCodec.decodeStoredBlocks(translationBlocks)
        .firstOrNull { it.id == TRANSLATION_TITLE_BLOCK_ID }
        ?.translatedText
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}
