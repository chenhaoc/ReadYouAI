package me.ash.reader.ui.page.home.flow

import me.ash.reader.domain.repository.ArticleTranslationPayloadCodec

data class ArticleListTranslationPreview(
    val title: String,
    val shortDescription: String,
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
    val translatedTexts =
        ArticleTranslationPayloadCodec.decodeStoredBlocks(translationBlocks).map { it.translatedText.trim() }
            .filter { it.isNotBlank() }
    if (translatedTexts.isEmpty()) {
        return ArticleListTranslationPreview(
            title = fallbackTitle,
            shortDescription = fallbackDescription,
        )
    }
    val translatedTitle = translatedTexts.first()
    val translatedSummary = translatedTexts.drop(1).take(2).joinToString(separator = " ")
    return ArticleListTranslationPreview(
        title = translatedTitle,
        shortDescription = translatedSummary.ifBlank { fallbackDescription },
    )
}
