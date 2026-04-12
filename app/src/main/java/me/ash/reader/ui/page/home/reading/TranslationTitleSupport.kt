package me.ash.reader.ui.page.home.reading

import me.ash.reader.domain.repository.ArticleTranslationPayloadCodec
import me.ash.reader.domain.repository.TranslatedArticleBlock

const val TRANSLATION_TITLE_BLOCK_ID = "list_title"
private const val PARAGRAPH_BLOCK_PREFIX = "paragraph_"
private const val HEADING_BLOCK_PREFIX = "heading_"

fun decodeStoredTranslationBlocks(translationBlocks: String?): List<TranslatedArticleBlock> {
    return ArticleTranslationPayloadCodec.decodeStoredBlocks(translationBlocks)
}

fun resolveTranslatedTitle(translationBlocks: String?): String? {
    return decodeStoredTranslationBlocks(translationBlocks)
        .firstOrNull { it.id == TRANSLATION_TITLE_BLOCK_ID }
        ?.translatedText
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

fun resolveTranslatedSummary(translationBlocks: String?): String? {
    val translatedBlocks = decodeStoredTranslationBlocks(translationBlocks)
    return (
        translatedBlocks.firstOrNull { it.id.startsWith(PARAGRAPH_BLOCK_PREFIX) }
            ?: translatedBlocks.firstOrNull {
                it.id != TRANSLATION_TITLE_BLOCK_ID && !it.id.startsWith(HEADING_BLOCK_PREFIX)
            }
        )
        ?.translatedText
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

fun selectTranslationsForCurrentBlocks(
    blocks: List<ArticleContentBlock>,
    storedBlocks: List<TranslatedArticleBlock>,
): List<TranslatedArticleBlock> {
    val storedMap = storedBlocks.associateBy { it.id }
    return blocks.mapNotNull { block -> storedMap[block.id] }
}

fun selectExtraTranslations(
    blocks: List<ArticleContentBlock>,
    storedBlocks: List<TranslatedArticleBlock>,
): List<TranslatedArticleBlock> {
    val blockIds = blocks.map { it.id }.toSet()
    return storedBlocks.filter { it.id !in blockIds }
}
