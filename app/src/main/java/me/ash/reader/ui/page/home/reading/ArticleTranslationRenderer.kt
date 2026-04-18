package me.ash.reader.ui.page.home.reading

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import me.ash.reader.domain.repository.ArticleTranslationPayloadCodec
import me.ash.reader.ui.component.reader.LocalTextContentWidth
import me.ash.reader.ui.component.reader.Reader
import org.jsoup.nodes.Entities

internal fun parseTranslatedBlockMap(translationBlocks: String?): Map<String, String> {
    return ArticleTranslationPayloadCodec.decodeStoredBlocks(translationBlocks).associate {
        it.id to it.translatedText
    }
}

internal fun buildWebViewBilingualContent(
    content: String,
    baseUrl: String,
    translationBlocks: String?,
): String {
    if (translationBlocks.isNullOrBlank()) return content
    val blocks = ArticleContentBlockParser.parse(content = content, baseUrl = baseUrl)
    val translatedMap = parseTranslatedBlockMap(translationBlocks)
    if (translatedMap.isEmpty()) return content
    return buildString {
        blocks.forEach { block ->
            append(block.originalHtml)
            val translated = translatedMap[block.id]
            if (!translated.isNullOrBlank()) {
                append(buildTranslatedHtml(block.type, translated))
            }
        }
    }
}

internal fun LazyListScope.BilingualReader(
    context: Context,
    subheadUpperCase: Boolean,
    link: String,
    blocks: List<ArticleContentBlock>,
    translatedBlockMap: Map<String, String>,
    onImageClick: ((imgUrl: String, altText: String) -> Unit)? = null,
    onLinkClick: (String) -> Unit,
) {
    blocks.forEach { block ->
        Reader(
            context = context,
            subheadUpperCase = subheadUpperCase,
            link = link,
            content = block.originalHtml,
            onImageClick = onImageClick,
            onLinkClick = onLinkClick,
        )
        val translated = translatedBlockMap[block.id]
        if (!translated.isNullOrBlank()) {
            item(key = "translation_${block.id}") {
                TranslationBlockText(type = block.type, text = translated)
            }
        }
    }
}

@Composable
private fun TranslationBlockText(
    type: ArticleContentBlockType,
    text: String,
) {
    val contentWidth = LocalTextContentWidth.current
    val typography =
        when (type) {
            ArticleContentBlockType.Heading -> MaterialTheme.typography.titleMedium
            else -> MaterialTheme.typography.bodyMedium
        }
    Text(
        text = text,
        modifier =
            Modifier.width(contentWidth)
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
        style = typography,
        color = MaterialTheme.colorScheme.primary,
        fontStyle = if (type == ArticleContentBlockType.Quote) FontStyle.Italic else FontStyle.Normal,
    )
}

private fun buildTranslatedHtml(
    type: ArticleContentBlockType,
    translatedText: String,
): String {
    val escapedText = Entities.escape(translatedText)
    val style =
        when (type) {
            ArticleContentBlockType.Heading ->
                "margin: 0 16px 18px; color: inherit; opacity: 0.92; font-weight: 600;"
            ArticleContentBlockType.ListItem ->
                "margin: 0 16px 14px 36px; color: inherit; opacity: 0.88;"
            ArticleContentBlockType.Quote ->
                "margin: 0 16px 18px; padding-left: 12px; border-left: 3px solid rgba(127,127,127,.35); color: inherit; opacity: 0.88; font-style: italic;"
            else ->
                "margin: 0 16px 18px; color: inherit; opacity: 0.88;"
        }
    return """<p class="ry-translation-block" style="$style">$escapedText</p>"""
}
