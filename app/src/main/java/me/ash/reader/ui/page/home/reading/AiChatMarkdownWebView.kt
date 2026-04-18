package me.ash.reader.ui.page.home.reading

import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.ceil
import me.ash.reader.infrastructure.preference.LocalReadingFonts
import me.ash.reader.infrastructure.preference.LocalReadingTextFontSize
import me.ash.reader.infrastructure.preference.LocalReadingTextLetterSpacing
import me.ash.reader.infrastructure.preference.LocalReadingTextLineHeight
import me.ash.reader.infrastructure.preference.ReadingFontsPreference
import me.ash.reader.ui.component.webview.JavaScriptInterface
import me.ash.reader.ui.component.webview.WebViewClient
import me.ash.reader.ui.component.webview.WebViewHtml
import me.ash.reader.ui.component.webview.WebViewLayout
import me.ash.reader.ui.component.webview.WebViewStyle
import me.ash.reader.ui.ext.ExternalFonts
import me.ash.reader.ui.theme.palette.alwaysLight

@Composable
internal fun AiChatMarkdownWebView(
    htmlFragment: String,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    if (htmlFragment.isBlank()) return

    val context = LocalContext.current
    val density = LocalDensity.current
    val uriHandler = LocalUriHandler.current
    val readingFonts = LocalReadingFonts.current
    val readingFontSize = LocalReadingTextFontSize.current
    val letterSpacing = LocalReadingTextLetterSpacing.current
    val readingLineHeight = LocalReadingTextLineHeight.current
    val fontSize = readingFontSize
    val lineHeight = readingLineHeight
    val selectionTextColor = Color.Black.toArgb()
    val selectionBgColor = (MaterialTheme.colorScheme.tertiaryContainer alwaysLight true).toArgb()
    val linkTextColor = MaterialTheme.colorScheme.primary.toArgb()
    val highlightColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f).toArgb()
    val codeTextColor = MaterialTheme.colorScheme.tertiary.toArgb()
    val codeBackgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest.toArgb()

    val fontPath =
        if (readingFonts is ReadingFontsPreference.External) {
            ExternalFonts.FontType.ReadingFont.toPath(context)
        } else if (readingFonts is ReadingFontsPreference.GoogleSans) {
            "/android_res/font/google_sans_flex.ttf"
        } else {
            null
        }

    val style =
        remember(
            fontSize,
            fontPath,
            lineHeight,
            letterSpacing,
            textColor,
            linkTextColor,
            selectionTextColor,
            selectionBgColor,
            codeTextColor,
            codeBackgroundColor,
            highlightColor,
        ) {
            buildAiChatWebViewStyle(
                fontSize = fontSize,
                fontPath = fontPath,
                lineHeight = lineHeight,
                letterSpacing = letterSpacing,
                textColor = textColor.toArgb(),
                boldTextColor = textColor.toArgb(),
                linkTextColor = linkTextColor,
                selectionTextColor = selectionTextColor,
                selectionBgColor = selectionBgColor,
                codeTextColor = codeTextColor,
                codeBgColor = codeBackgroundColor,
                highlightColor = highlightColor,
            )
        }
    val script = remember { aiChatHeightScript() }
    val pageHtml =
        remember(style, htmlFragment, script) {
            WebViewHtml.HTML.format(style, "", htmlFragment, script)
        }

    val minimumHeightPx =
        with(density) {
            1.dp.roundToPx()
        }
    var contentHeightPx by remember(pageHtml) { mutableStateOf(minimumHeightPx) }
    val webView by
        remember(readingFonts, uriHandler) {
            mutableStateOf(
                WebViewLayout.get(
                    context = context,
                    readingFontsPreference = readingFonts,
                    webViewClient =
                        WebViewClient(
                            context = context,
                            refererDomain = null,
                            onOpenLink = { url ->
                                uriHandler.openUri(url)
                            },
                        ),
                    onContentHeightChanged = { cssHeight ->
                        val heightPx =
                            ceil(cssHeight * density.density).toInt() +
                                with(density) { 4.dp.roundToPx() }
                        contentHeightPx = heightPx.coerceAtLeast(minimumHeightPx)
                    },
                ).apply {
                    isVerticalScrollBarEnabled = false
                    overScrollMode = WebView.OVER_SCROLL_NEVER
                }
            )
        }

    AndroidView(
        modifier =
            modifier.fillMaxWidth()
                .height(with(density) { contentHeightPx.toDp() }),
        factory = {
            webView
        },
        update = { view ->
            if (view.tag != pageHtml) {
                view.tag = pageHtml
                view.settings.defaultFontSize = fontSize
                view.loadDataWithBaseURL(
                    null,
                    pageHtml,
                    "text/HTML",
                    "UTF-8",
                    null,
                )
            }
        },
    )
}

private fun buildAiChatWebViewStyle(
    fontSize: Int,
    fontPath: String?,
    lineHeight: Float,
    letterSpacing: Float,
    textColor: Int,
    boldTextColor: Int,
    linkTextColor: Int,
    selectionTextColor: Int,
    selectionBgColor: Int,
    codeTextColor: Int,
    codeBgColor: Int,
    highlightColor: Int,
): String =
    buildString {
        append(
            WebViewStyle.get(
                fontSize = fontSize,
                fontPath = fontPath,
                lineHeight = lineHeight,
                letterSpacing = letterSpacing,
                textMargin = 0,
                textColor = textColor,
                textBold = false,
                textAlign = "start",
                boldTextColor = boldTextColor,
                subheadBold = true,
                subheadUpperCase = false,
                imgMargin = 0,
                imgBorderRadius = 12,
                linkTextColor = linkTextColor,
                codeTextColor = codeTextColor,
                codeBgColor = codeBgColor,
                tableMargin = 0,
                selectionTextColor = selectionTextColor,
                selectionBgColor = selectionBgColor,
            )
        )
        append(
            """

.ry-ai-chat-markdown > :first-child {
    margin-top: 0 !important;
}

.ry-ai-chat-markdown > :last-child {
    margin-bottom: 0 !important;
}

.ry-ai-chat-markdown p,
.ry-ai-chat-markdown h1,
.ry-ai-chat-markdown h2,
.ry-ai-chat-markdown h3,
.ry-ai-chat-markdown h4,
.ry-ai-chat-markdown h5,
.ry-ai-chat-markdown h6,
.ry-ai-chat-markdown blockquote,
.ry-ai-chat-markdown pre,
.ry-ai-chat-markdown hr,
.ry-ai-chat-markdown table {
    margin-left: 0 !important;
    margin-right: 0 !important;
}

.ry-ai-chat-markdown ul,
.ry-ai-chat-markdown ol {
    margin-top: 0.45em !important;
    margin-bottom: 0.45em !important;
    padding-left: 1.3em !important;
}

.ry-ai-chat-markdown li {
    margin-left: 0 !important;
}

.ry-ai-chat-markdown mark {
    background-color: ${highlightColor.toCssColor()} !important;
    color: inherit !important;
    padding: 0 2px;
    border-radius: 3px;
}

.ry-ai-chat-markdown del {
    text-decoration: line-through !important;
}

.ry-ai-chat-code-language {
    margin: 0 0 6px !important;
    color: ${linkTextColor.toCssColor()} !important;
    font-size: 0.8em !important;
    font-weight: 600 !important;
    letter-spacing: 0.02em !important;
}

.ry-ai-chat-task-marker {
    color: var(--bold-text-color) !important;
    font-weight: 600 !important;
}
            """.trimIndent()
        )
    }

private fun aiChatHeightScript(): String =
    """

(function() {
    function reportHeight() {
        if (!window.${JavaScriptInterface.NAME} || !window.${JavaScriptInterface.NAME}.onContentHeightChanged) {
            return;
        }
        const body = document.body;
        const doc = document.documentElement;
        const height = Math.max(
            body ? body.scrollHeight : 0,
            body ? body.offsetHeight : 0,
            doc ? doc.clientHeight : 0,
            doc ? doc.scrollHeight : 0,
            doc ? doc.offsetHeight : 0
        );
        window.${JavaScriptInterface.NAME}.onContentHeightChanged(Math.ceil(height));
    }

    function scheduleReport() {
        if (window.requestAnimationFrame) {
            window.requestAnimationFrame(reportHeight);
        } else {
            setTimeout(reportHeight, 0);
        }
    }

    window.addEventListener('load', scheduleReport);
    window.addEventListener('resize', scheduleReport);
    document.addEventListener('readystatechange', scheduleReport);

    if (window.ResizeObserver && document.body) {
        const observer = new ResizeObserver(scheduleReport);
        observer.observe(document.body);
    }

    document.querySelectorAll('img').forEach((img) => {
        if (!img.complete) {
            img.addEventListener('load', scheduleReport);
            img.addEventListener('error', scheduleReport);
        }
    });

    scheduleReport();
    setTimeout(scheduleReport, 60);
    setTimeout(scheduleReport, 240);
})();
    """.trimIndent()

private fun Int.toCssColor(): String = String.format("#%06X", 0xFFFFFF and this)
