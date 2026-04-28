package me.ash.reader.ui.page.home.flow

import me.ash.reader.domain.repository.ArticleTranslationPayloadCodec
import me.ash.reader.ui.page.home.reading.ArticleContentBlock
import me.ash.reader.ui.page.home.reading.ArticleContentBlockType
import me.ash.reader.ui.page.home.reading.TranslationSourceBlock
import me.ash.reader.ui.page.home.reading.TRANSLATION_TITLE_BLOCK_ID
import me.ash.reader.ui.page.home.reading.resolveTranslatedSummary
import me.ash.reader.ui.page.home.reading.resolveTranslatedTitle

data class ArticleListTranslationPreview(
    val title: String,
    val shortDescription: String,
    val isTitleTranslated: Boolean = false,
    val isShortDescriptionTranslated: Boolean = false,
)

fun buildListTranslationTargetIds(
    visibleArticleIds: List<String>,
    subsequentArticleIds: List<String>,
    prefetchCount: Int = 4,
): List<String> {
    if (visibleArticleIds.isEmpty()) return emptyList()
    return (visibleArticleIds + subsequentArticleIds.take(prefetchCount)).distinct()
}

fun resolveTranslatedListPreview(
    translationBlocks: String?,
    fallbackTitle: String,
    fallbackDescription: String,
): ArticleListTranslationPreview {
    val translatedBlocks = ArticleTranslationPayloadCodec.decodeStoredBlocks(translationBlocks)
    if (translatedBlocks.isEmpty()) {
        return ArticleListTranslationPreview(
            title = fallbackTitle,
            shortDescription = fallbackDescription,
        )
    }
    val translatedTitle = resolveTranslatedTitle(translationBlocks)
    val translatedSummary = resolveTranslatedSummary(translationBlocks)
    return ArticleListTranslationPreview(
        title = translatedTitle ?: fallbackTitle,
        shortDescription = translatedSummary?.ifBlank { fallbackDescription } ?: fallbackDescription,
        isTitleTranslated = translatedTitle != null,
        isShortDescriptionTranslated = !translatedSummary.isNullOrBlank(),
    )
}

fun buildListTranslationSourceBlocks(
    articleTitle: String,
    blocks: List<ArticleContentBlock>,
): List<TranslationSourceBlock> {
    val titleBlock =
        articleTitle.trim()
            .takeIf { it.isNotBlank() }
            ?.let {
                TranslationSourceBlock(
                    id = TRANSLATION_TITLE_BLOCK_ID,
                    type = "title",
                    text = it,
                )
            }
    val leadBlock =
        blocks.firstOrNull { it.type == ArticleContentBlockType.Paragraph && it.isTranslationEligible }
            ?: blocks.firstOrNull {
                it.isTranslationEligible && it.type != ArticleContentBlockType.Heading
            }
            ?: blocks.firstOrNull { it.isTranslationEligible }
    return listOfNotNull(
        titleBlock,
        leadBlock?.let {
            TranslationSourceBlock(
                id = it.id,
                type = it.type.name.lowercase(),
                text = it.translationText.orEmpty(),
            )
        },
    )
}
