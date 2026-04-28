package me.ash.reader.ui.page.home.reading

import android.webkit.WebView
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import java.util.Date
import me.ash.reader.infrastructure.preference.LocalReadingRenderer
import me.ash.reader.infrastructure.preference.LocalReadingSubheadUpperCase
import me.ash.reader.infrastructure.preference.ReadingRendererPreference
import me.ash.reader.ui.component.reader.LocalTextContentWidth
import me.ash.reader.ui.component.reader.Reader
import me.ash.reader.ui.component.scrollbar.drawVerticalScrollIndicator
import me.ash.reader.ui.component.webview.RYWebView
import me.ash.reader.ui.ext.extractDomain
import me.ash.reader.ui.ext.roundClick

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Content(
    modifier: Modifier = Modifier,
    content: String,
    rawDescription: String,
    shortDescription: String,
    aiSummary: String?,
    isAiSummaryLoading: Boolean,
    aiSummaryError: String?,
    isAiSummaryExpanded: Boolean,
    translatedContentBlocks: String?,
    contentBlocks: List<ArticleContentBlock>,
    translatedBlockMap: Map<String, String>,
    feedName: String,
    title: String,
    author: String? = null,
    link: String? = null,
    publishedDate: Date,
    scrollState: ScrollState,
    listState: LazyListState,
    isLoading: Boolean,
    contentPadding: PaddingValues = PaddingValues(),
    onImageClick: ((imgUrl: String, altText: String) -> Unit)? = null,
    onAiSummaryToggleExpand: () -> Unit = {},
    onAiSummaryVisibilityChanged: (Boolean) -> Unit = {},
    onWebViewReady: (WebView) -> Unit = {},
) {
    val context = LocalContext.current
    val subheadUpperCase = LocalReadingSubheadUpperCase.current
    val renderer = LocalReadingRenderer.current

    val textContentWidth = LocalTextContentWidth.current
    val maxWidthModifier = Modifier.widthIn(max = textContentWidth)
    val uriHandler = LocalUriHandler.current
    val translatedTitle = resolveTranslatedTitle(translatedContentBlocks)
    val hasInlineTranslations =
        remember(contentBlocks, translatedBlockMap) {
            hasInlineTranslatedBlocks(
                blocks = contentBlocks,
                translatedBlockMap = translatedBlockMap,
            )
        }

    val headline =
        @Composable {
            Column(modifier = Modifier.then(maxWidthModifier).padding(horizontal = 12.dp)) {
                DisableSelection {
                    Metadata(
                        feedName = feedName,
                        title = title,
                        rawDescription = rawDescription,
                        shortDescription = shortDescription,
                        translatedTitle = translatedTitle,
                        author = author,
                        publishedDate = publishedDate,
                        modifier = Modifier.roundClick { link?.let { uriHandler.openUri(it) } },
                    )
                }
            }
        }

    val summarySection =
        @Composable {
            Column(modifier = Modifier.then(maxWidthModifier).padding(horizontal = 12.dp)) {
                AiSummaryCard(
                    summary = aiSummary.orEmpty(),
                    isLoading = isAiSummaryLoading,
                    error = aiSummaryError,
                    isExpanded = isAiSummaryExpanded,
                    onToggleExpanded = onAiSummaryToggleExpand,
                    onVisibilityChanged = onAiSummaryVisibilityChanged,
                )
            }
        }

    if (isLoading) {
        Column { LoadingIndicator(modifier = Modifier.size(56.dp)) }
    } else {

        when (renderer) {
            ReadingRendererPreference.WebView -> {
                Column(
                    modifier =
                        modifier
                            .padding(top = contentPadding.calculateTopPadding())
                            .fillMaxSize()
                            .drawVerticalScrollIndicator(scrollState)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Column(modifier = Modifier.then(maxWidthModifier)) {
                            // Top bar height
                            Spacer(modifier = Modifier.height(64.dp))
                            // padding
                            headline()
                            summarySection()
                            Spacer(modifier = Modifier.height(16.dp))

                            RYWebView(
                                modifier = Modifier.fillMaxWidth(),
                                content =
                                    if (hasInlineTranslations) {
                                        buildWebViewBilingualContent(
                                            content = content,
                                            blocks = contentBlocks,
                                            translatedBlockMap = translatedBlockMap,
                                        )
                                    } else {
                                        content
                                    },
                                refererDomain = link.extractDomain(),
                                onImageClick = onImageClick,
                                onWebViewReady = onWebViewReady,
                                onScrollDelta = { delta -> scrollState.dispatchRawDelta(delta) },
                            )
                            Spacer(modifier = Modifier.height(128.dp))
                            Spacer(
                                modifier = Modifier.height(contentPadding.calculateBottomPadding())
                            )
                        }
                    }
                }
            }

            ReadingRendererPreference.NativeComponent -> {
                SelectionContainer {
                    LazyColumn(
                        modifier = modifier.fillMaxSize().drawVerticalScrollIndicator(listState),
                        state = listState,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        item(key = "reading_header") {
                            // Top bar height
                            Spacer(modifier = Modifier.height(64.dp))
                            // padding
                            Spacer(modifier = Modifier.height(contentPadding.calculateTopPadding()))
                            headline()
                        }

                        item(key = AI_SUMMARY_NATIVE_ITEM_KEY) { summarySection() }

                        item(key = "reading_ai_summary_spacing") {
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        if (!hasInlineTranslations) {
                            Reader(
                                context = context,
                                subheadUpperCase = subheadUpperCase.value,
                                link = link ?: "",
                                content = content,
                                onImageClick = onImageClick,
                                onLinkClick = { uriHandler.openUri(it) },
                            )
                        } else {
                            BilingualReader(
                                context = context,
                                subheadUpperCase = subheadUpperCase.value,
                                link = link ?: "",
                                blocks = contentBlocks,
                                translatedBlockMap = translatedBlockMap,
                                onImageClick = onImageClick,
                                onLinkClick = { uriHandler.openUri(it) },
                            )
                        }

                        item(key = "reading_footer") {
                            Spacer(modifier = Modifier.height(128.dp))
                            Spacer(
                                modifier = Modifier.height(contentPadding.calculateBottomPadding())
                            )
                        }
                    }
                }
            }
        }
    }
}
