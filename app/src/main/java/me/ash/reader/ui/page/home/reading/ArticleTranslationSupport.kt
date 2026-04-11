package me.ash.reader.ui.page.home.reading

import me.ash.reader.domain.repository.TranslationRequestChunker
import me.ash.reader.ui.page.home.reading.ArticleContentBlock

data class FeedTranslationSettings(
    val isTranslationEnabled: Boolean,
    val isAutoTranslate: Boolean,
)

fun normalizeFeedTranslationSettings(
    isTranslationEnabled: Boolean,
    isAutoTranslate: Boolean,
    preferAutoTranslate: Boolean = false,
): FeedTranslationSettings {
    return when {
        preferAutoTranslate && isAutoTranslate ->
            FeedTranslationSettings(
                isTranslationEnabled = true,
                isAutoTranslate = true,
            )
        !isTranslationEnabled ->
            FeedTranslationSettings(
                isTranslationEnabled = false,
                isAutoTranslate = false,
            )
        else ->
            FeedTranslationSettings(
                isTranslationEnabled = true,
                isAutoTranslate = isAutoTranslate,
            )
    }
}

fun buildPrioritizedTranslationBatch(
    blocks: List<ArticleContentBlock>,
    translatedBlockIds: Set<String>,
    preferredStartIndex: Int,
    maxEstimatedOutputTokens: Int = 450,
): List<me.ash.reader.ui.page.home.reading.TranslationSourceBlock> {
    if (blocks.isEmpty()) return emptyList()

    val normalizedStartIndex = preferredStartIndex.coerceIn(0, blocks.lastIndex)
    val prioritizedBlocks =
        blocks.withIndex()
            .filter { it.value.isTranslationEligible && it.value.id !in translatedBlockIds }
            .sortedWith(
                compareBy<IndexedValue<ArticleContentBlock>> {
                    if (it.index >= normalizedStartIndex) 0 else 1
                }.thenBy {
                    if (it.index >= normalizedStartIndex) it.index else it.index + blocks.size
                }
            )

    val batch = mutableListOf<TranslationSourceBlock>()
    var estimatedTokens = 0

    for (indexedBlock in prioritizedBlocks) {
        val sourceBlock =
            TranslationSourceBlock(
                id = indexedBlock.value.id,
                type = indexedBlock.value.type.name.lowercase(),
                text = indexedBlock.value.translationText.orEmpty(),
            )
        val nextEstimatedTokens =
            estimatedTokens + TranslationRequestChunker.estimateOutputTokens(sourceBlock.text)
        if (batch.isNotEmpty() && nextEstimatedTokens > maxEstimatedOutputTokens) {
            break
        }
        batch += sourceBlock
        estimatedTokens = nextEstimatedTokens
    }

    return batch
}
