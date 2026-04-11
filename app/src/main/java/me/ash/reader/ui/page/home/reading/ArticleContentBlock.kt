package me.ash.reader.ui.page.home.reading

enum class ArticleContentBlockType {
    Heading,
    Paragraph,
    Quote,
    ListItem,
    CodeBlock,
    Image,
    Video,
    Divider,
    Table,
}

data class ArticleContentBlock(
    val id: String,
    val type: ArticleContentBlockType,
    val originalHtml: String,
    val translationText: String? = null,
) {
    val isTranslationEligible: Boolean
        get() = !translationText.isNullOrBlank()
}

data class TranslationSourceBlock(
    val id: String,
    val type: String,
    val text: String,
)
