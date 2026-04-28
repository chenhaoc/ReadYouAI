package me.ash.reader.ui.page.home.reading

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.ash.reader.domain.repository.ArticleTranslationPayloadCodec
import me.ash.reader.ui.component.reader.LocalTextContentWidth
import me.ash.reader.ui.component.reader.Reader
import me.ash.reader.ui.component.reader.bodyStyle
import me.ash.reader.ui.component.reader.textHorizontalPadding
import org.jsoup.nodes.Entities

internal fun parseTranslatedBlockMap(translationBlocks: String?): Map<String, String> {
    return ArticleTranslationPayloadCodec.decodeStoredBlocks(translationBlocks).associate {
        it.id to it.translatedText
    }
}

internal fun buildWebViewBilingualContent(
    content: String,
    blocks: List<ArticleContentBlock>,
    translatedBlockMap: Map<String, String>,
): String {
    if (!hasInlineTranslatedBlocks(blocks, translatedBlockMap)) return content
    return buildString {
        blocks.forEach { block ->
            append(block.originalHtml)
            val translated = translatedBlockMap[block.id]
            if (!translated.isNullOrBlank()) {
                append(buildTranslatedHtml(block.type, translated))
            }
        }
    }
}

internal fun hasInlineTranslatedBlocks(
    blocks: List<ArticleContentBlock>,
    translatedBlockMap: Map<String, String>,
): Boolean {
    if (blocks.isEmpty() || translatedBlockMap.isEmpty()) return false
    return blocks.any { !translatedBlockMap[it.id].isNullOrBlank() }
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
    val baseHorizontalPadding = textHorizontalPadding().dp
    val translationColor = MaterialTheme.colorScheme.primary
    val quoteBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val (linePadding, startPadding, endPadding, topPadding, bottomPadding) =
        when (type) {
            ArticleContentBlockType.ListItem ->
                TranslationPadding(
                    line = null,
                    start = baseHorizontalPadding + 36.dp,
                    end = baseHorizontalPadding + 16.dp,
                    top = 20.dp,
                    bottom = 14.dp,
                )
            ArticleContentBlockType.Quote ->
                TranslationPadding(
                    line = baseHorizontalPadding + 16.dp,
                    start = baseHorizontalPadding + 31.dp,
                    end = baseHorizontalPadding + 16.dp,
                    top = 20.dp,
                    bottom = 18.dp,
                )
            else ->
                TranslationPadding(
                    line = null,
                    start = baseHorizontalPadding + 16.dp,
                    end = baseHorizontalPadding + 16.dp,
                    top = 20.dp,
                    bottom = 18.dp,
                )
        }
    val baseStyle = bodyStyle()
    val typography =
        baseStyle.copy(
            color = translationColor,
            fontWeight =
                if (type == ArticleContentBlockType.Heading) FontWeight.SemiBold
                else baseStyle.fontWeight,
            fontStyle = if (type == ArticleContentBlockType.Quote) FontStyle.Italic else FontStyle.Normal,
        )
    Box(
        modifier =
            Modifier.width(contentWidth)
                .padding(top = topPadding, bottom = bottomPadding)
                .then(
                    if (linePadding != null) {
                        Modifier
                            .drawBehind {
                                val lineX = linePadding.toPx()
                                drawLine(
                                    color = quoteBorderColor,
                                    start = Offset(lineX, 0f),
                                    end = Offset(lineX, size.height),
                                    strokeWidth = 3.dp.toPx(),
                                )
                            }
                    } else {
                        Modifier
                    }
                )
    ) {
        Text(
            text = text,
            modifier =
                Modifier.fillMaxWidth()
                    .padding(start = startPadding, end = endPadding),
            style = typography,
        )
    }
}

private data class TranslationPadding(
    val line: androidx.compose.ui.unit.Dp?,
    val start: androidx.compose.ui.unit.Dp,
    val end: androidx.compose.ui.unit.Dp,
    val top: androidx.compose.ui.unit.Dp,
    val bottom: androidx.compose.ui.unit.Dp,
)

private fun buildTranslatedHtml(
    type: ArticleContentBlockType,
    translatedText: String,
): String {
    val escapedText = Entities.escape(translatedText)
    val style =
        when (type) {
            ArticleContentBlockType.Heading ->
                "margin: 0 16px 18px; color: var(--link-text-color); font-weight: 600;"
            ArticleContentBlockType.ListItem ->
                "margin: 0 16px 14px 36px; color: var(--link-text-color);"
            ArticleContentBlockType.Quote ->
                "margin: 0 16px 18px; padding-left: 12px; border-left: 3px solid rgba(127,127,127,.35); color: var(--link-text-color); font-style: italic;"
            else ->
                "margin: 0 16px 18px; color: var(--link-text-color);"
        }
    return """<p class="ry-translation-block" style="$style">$escapedText</p>"""
}
