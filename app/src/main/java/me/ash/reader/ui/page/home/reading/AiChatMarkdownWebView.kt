package me.ash.reader.ui.page.home.reading

import android.view.MotionEvent
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt
import me.ash.reader.domain.model.ai.AiChatMessage
import me.ash.reader.infrastructure.preference.LocalDarkTheme
import me.ash.reader.infrastructure.preference.LocalReadingFonts
import me.ash.reader.infrastructure.preference.LocalReadingTextFontSize
import me.ash.reader.infrastructure.preference.LocalReadingTextLetterSpacing
import me.ash.reader.infrastructure.preference.LocalReadingTextLineHeight
import me.ash.reader.infrastructure.preference.ReadingFontsPreference
import me.ash.reader.ui.component.webview.WebViewClient
import me.ash.reader.ui.component.webview.WebViewHtml
import me.ash.reader.ui.component.webview.WebViewLayout
import me.ash.reader.ui.component.webview.WebViewStyle
import me.ash.reader.ui.ext.ExternalFonts
import me.ash.reader.ui.theme.palette.alwaysLight

@Composable
internal fun AiChatConversationContent(
    messages: List<AiChatMessage>,
    isSending: Boolean,
    modifier: Modifier = Modifier,
) {
    if (messages.isEmpty() && !isSending) return

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val readingFonts = LocalReadingFonts.current
    val readingFontSize = LocalReadingTextFontSize.current
    val letterSpacing = LocalReadingTextLetterSpacing.current
    val readingLineHeight = LocalReadingTextLineHeight.current
    val chatBodyStyle = MaterialTheme.typography.bodyMedium
    val fontSize =
        if (chatBodyStyle.fontSize.type == TextUnitType.Sp) {
            chatBodyStyle.fontSize.value.roundToInt()
        } else {
            readingFontSize
        }
    val lineHeight =
        if (
            chatBodyStyle.fontSize.type == TextUnitType.Sp &&
            chatBodyStyle.lineHeight.type == TextUnitType.Sp &&
            chatBodyStyle.fontSize.value > 0f
        ) {
            (chatBodyStyle.lineHeight.value / chatBodyStyle.fontSize.value / 1.5f).coerceAtLeast(0.8f)
        } else {
            readingLineHeight
        }
    val useDarkTheme = LocalDarkTheme.current.isDarkTheme()
    val assistantTextColor = MaterialTheme.colorScheme.onSurface
    val userTextColor = MaterialTheme.colorScheme.onPrimaryContainer
    val selectionTextColor = Color.Black.toArgb()
    val selectionBgColor = (MaterialTheme.colorScheme.tertiaryContainer alwaysLight true).toArgb()
    val linkTextColor = MaterialTheme.colorScheme.primary.toArgb()
    val highlightColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f).toArgb()
    val codeTextColor = MaterialTheme.colorScheme.tertiary.toArgb()
    val codeBackgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest.toArgb()
    val tableBorderColor = MaterialTheme.colorScheme.outlineVariant.toArgb()
    val tableHeaderBackgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest.toArgb()
    val tableAltRowBackgroundColor = MaterialTheme.colorScheme.surfaceContainerLow.toArgb()
    val assistantBubbleColor = MaterialTheme.colorScheme.surfaceContainerHigh.toArgb()
    val userBubbleColor = MaterialTheme.colorScheme.primaryContainer.toArgb()

    val fontPath =
        if (readingFonts is ReadingFontsPreference.External) {
            ExternalFonts.FontType.ReadingFont.toPath(context)
        } else if (readingFonts is ReadingFontsPreference.GoogleSans) {
            "/android_res/font/google_sans_flex.ttf"
        } else {
            null
        }

    val conversationHtml = remember(messages, isSending) { buildAiChatConversationHtml(messages, isSending) }
    val style =
        remember(
            fontSize,
            fontPath,
            lineHeight,
            letterSpacing,
            assistantTextColor,
            assistantBubbleColor,
            userTextColor,
            userBubbleColor,
            linkTextColor,
            selectionTextColor,
            selectionBgColor,
            codeTextColor,
            codeBackgroundColor,
            highlightColor,
            tableBorderColor,
            tableHeaderBackgroundColor,
            tableAltRowBackgroundColor,
            useDarkTheme,
        ) {
            buildAiChatWebViewStyle(
                fontSize = fontSize,
                fontPath = fontPath,
                lineHeight = lineHeight,
                letterSpacing = letterSpacing,
                textColor = assistantTextColor.toArgb(),
                boldTextColor = assistantTextColor.toArgb(),
                linkTextColor = linkTextColor,
                selectionTextColor = selectionTextColor,
                selectionBgColor = selectionBgColor,
                codeTextColor = codeTextColor,
                codeBgColor = codeBackgroundColor,
                highlightColor = highlightColor,
                tableBorderColor = tableBorderColor,
                tableHeaderBackgroundColor = tableHeaderBackgroundColor,
                tableAltRowBackgroundColor = tableAltRowBackgroundColor,
                useDarkTheme = useDarkTheme,
                extraCss =
                    buildAiChatConversationCss(
                        assistantTextColor = assistantTextColor.toArgb(),
                        assistantBubbleColor = assistantBubbleColor,
                        userTextColor = userTextColor.toArgb(),
                        userBubbleColor = userBubbleColor,
                    ),
            )
        }
    val pageHtml =
        remember(style, conversationHtml) {
            WebViewHtml.HTML.format(style, "", conversationHtml, "")
        }
    val nestedScrollInterop = rememberNestedScrollInteropConnection()
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
                ).apply {
                    isVerticalScrollBarEnabled = false
                    overScrollMode = WebView.OVER_SCROLL_NEVER
                    setOnTouchListener { view, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN,
                            MotionEvent.ACTION_MOVE -> {
                                view.parent?.requestDisallowInterceptTouchEvent(true)
                            }

                            MotionEvent.ACTION_UP,
                            MotionEvent.ACTION_CANCEL -> {
                                view.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                        }
                        false
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        settings.isAlgorithmicDarkeningAllowed = false
                    }
                }
            )
        }

    AndroidView(
        modifier = modifier.fillMaxSize().nestedScroll(nestedScrollInterop),
        factory = { webView },
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
                scheduleAiChatScrollToBottom(view, pageHtml)
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
    tableBorderColor: Int,
    tableHeaderBackgroundColor: Int,
    tableAltRowBackgroundColor: Int,
    useDarkTheme: Boolean,
    extraCss: String = "",
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

html,
body,
main,
article,
.ry-ai-chat-markdown {
    background: transparent !important;
    color: ${textColor.toCssColor()} !important;
}

body {
    color-scheme: ${if (useDarkTheme) "dark" else "light"};
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

.ry-ai-chat-table-wrap {
    width: 100%;
    overflow-x: auto;
    margin: 0.45em 0 !important;
    border: 1px solid ${tableBorderColor.toCssColor()};
    border-radius: 10px;
}

.ry-ai-chat-table-wrap table {
    display: table !important;
    width: max-content !important;
    min-width: 100% !important;
    border-collapse: collapse !important;
    table-layout: auto !important;
    margin: 0 !important;
}

.ry-ai-chat-table-wrap thead {
    display: table-header-group !important;
}

.ry-ai-chat-table-wrap tbody {
    display: table-row-group !important;
}

.ry-ai-chat-table-wrap tr {
    display: table-row !important;
}

.ry-ai-chat-table-wrap th,
.ry-ai-chat-table-wrap td {
    display: table-cell !important;
    min-width: 88px;
    padding: 8px 10px !important;
    font-size: 0.92em !important;
    border: 1px solid ${tableBorderColor.toCssColor()} !important;
    vertical-align: top !important;
    text-align: left !important;
    white-space: pre-wrap !important;
    word-break: break-word;
}

.ry-ai-chat-table-wrap th {
    background-color: ${tableHeaderBackgroundColor.toCssColor()} !important;
    font-weight: 600 !important;
}

.ry-ai-chat-table-wrap tbody tr:nth-child(even) td {
    background-color: ${tableAltRowBackgroundColor.toCssColor()} !important;
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
        append(extraCss)
    }

private fun buildAiChatConversationCss(
    assistantTextColor: Int,
    assistantBubbleColor: Int,
    userTextColor: Int,
    userBubbleColor: Int,
): String =
    """

.ry-ai-chat-conversation {
    display: flex;
    flex-direction: column;
    gap: 8px;
    min-height: 100%;
    padding: 0 0 8px;
    box-sizing: border-box;
}

.ry-ai-chat-message {
    display: flex;
    width: 100%;
}

.ry-ai-chat-message.user {
    justify-content: flex-end;
}

.ry-ai-chat-message.assistant {
    justify-content: flex-start;
}

.ry-ai-chat-bubble {
    box-sizing: border-box;
    max-width: 88%;
    padding: 8px 12px;
    border-radius: 20px;
    overflow: hidden;
}

.ry-ai-chat-message.assistant .ry-ai-chat-bubble {
    background: ${assistantBubbleColor.toCssColor()} !important;
    color: ${assistantTextColor.toCssColor()} !important;
}

.ry-ai-chat-message.user .ry-ai-chat-bubble {
    background: ${userBubbleColor.toCssColor()} !important;
    color: ${userTextColor.toCssColor()} !important;
}

.ry-ai-chat-message.user .ry-ai-chat-bubble,
.ry-ai-chat-message.user .ry-ai-chat-bubble * {
    color: ${userTextColor.toCssColor()} !important;
}

.ry-ai-chat-typing {
    display: flex;
    align-items: center;
    gap: 6px;
    min-height: 20px;
}

.ry-ai-chat-typing span {
    width: 6px;
    height: 6px;
    border-radius: 999px;
    background: currentColor;
    opacity: 0.32;
    animation: ry-ai-chat-typing 1.2s infinite ease-in-out;
}

.ry-ai-chat-typing span:nth-child(2) {
    animation-delay: 0.16s;
}

.ry-ai-chat-typing span:nth-child(3) {
    animation-delay: 0.32s;
}

@keyframes ry-ai-chat-typing {
    0%, 80%, 100% {
        transform: scale(0.72);
        opacity: 0.24;
    }
    40% {
        transform: scale(1);
        opacity: 0.88;
    }
}
    """.trimIndent()

private fun Int.toCssColor(): String = String.format("#%06X", 0xFFFFFF and this)

private fun scheduleAiChatScrollToBottom(
    webView: WebView,
    pageHtml: String,
) {
    val script = "window.scrollTo(0, document.body.scrollHeight);"
    listOf(16L, 96L, 240L).forEach { delayMs ->
        webView.postDelayed(
            {
                if (webView.tag == pageHtml) {
                    webView.evaluateJavascript(script, null)
                }
            },
            delayMs,
        )
    }
}
