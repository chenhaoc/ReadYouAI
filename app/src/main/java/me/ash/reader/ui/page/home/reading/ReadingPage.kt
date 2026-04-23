package me.ash.reader.ui.page.home.reading

import android.content.ClipboardManager
import android.webkit.WebView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.ash.reader.R
import me.ash.reader.infrastructure.android.TextToSpeechManager
import me.ash.reader.infrastructure.preference.LocalOpenLink
import me.ash.reader.infrastructure.preference.LocalOpenLinkSpecificBrowser
import me.ash.reader.infrastructure.preference.LocalPullToSwitchArticle
import me.ash.reader.infrastructure.preference.LocalReadingAutoHideToolbar
import me.ash.reader.infrastructure.preference.LocalReadingBoldCharacters
import me.ash.reader.infrastructure.preference.LocalReadingRenderer
import me.ash.reader.infrastructure.preference.LocalReadingTextLineHeight
import me.ash.reader.infrastructure.preference.ReadingRendererPreference
import me.ash.reader.infrastructure.preference.not
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.ext.openURL
import me.ash.reader.ui.ext.showToast
import me.ash.reader.ui.page.adaptive.ArticleListReaderViewModel
import me.ash.reader.ui.page.adaptive.NavigationAction
import me.ash.reader.ui.page.adaptive.ReaderState
import me.ash.reader.ui.page.home.reading.tts.TtsButton

private const val UPWARD = 1
private const val DOWNWARD = -1
internal const val AI_SUMMARY_NATIVE_ITEM_KEY = "reading_ai_summary"

private sealed interface SummaryReturnTarget {
    data class Scroll(val value: Int) : SummaryReturnTarget
    data class List(val index: Int, val offset: Int) : SummaryReturnTarget
}

private class SummaryNavigationController {
    var jumpToSummary: (suspend () -> Unit)? = null
    var restoreReturnTarget: ((SummaryReturnTarget) -> Unit)? = null
}

private suspend fun WebView.captureSelectedText(): String? =
    suspendCancellableCoroutine { continuation ->
        post {
            evaluateJavascript(
                "(function(){return window.getSelection ? window.getSelection().toString() : '';})()",
            ) { rawValue ->
                val value =
                    rawValue
                        ?.removePrefix("\"")
                        ?.removeSuffix("\"")
                        ?.replace("\\n", "\n")
                        ?.replace("\\\"", "\"")
                        ?.replace("\\\\", "\\")
                        ?.trim()
                continuation.resume(value?.takeIf { it.isNotEmpty() })
            }
        }
    }

private fun isNativeSummaryItemVisible(
    layoutInfo: LazyListLayoutInfo,
    minVisibleHeightPx: Float,
): Boolean {
    val summaryItem =
        layoutInfo.visibleItemsInfo.firstOrNull { it.key == AI_SUMMARY_NATIVE_ITEM_KEY } ?: return false
    val visibleTop = max(summaryItem.offset, layoutInfo.viewportStartOffset)
    val visibleBottom = min(summaryItem.offset + summaryItem.size, layoutInfo.viewportEndOffset)
    val visibleHeight = (visibleBottom - visibleTop).toFloat().coerceAtLeast(0f)
    return visibleHeight >= min(minVisibleHeightPx, summaryItem.size.toFloat())
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReadingPage(
    //    navController: NavHostController,
    viewModel: ArticleListReaderViewModel,
    navigationAction: NavigationAction,
    onLoadArticle: (String, Int) -> Unit,
    onNavAction: (NavigationAction) -> Unit,
    onNavigateToStylePage: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val isPullToSwitchArticleEnabled = LocalPullToSwitchArticle.current.value
    val readingUiState = viewModel.readingUiState.collectAsStateValue()
    val readerState = viewModel.readerStateStateFlow.collectAsStateValue()
    val boldCharacters = LocalReadingBoldCharacters.current
    val readingRenderer = LocalReadingRenderer.current
    val openLink = LocalOpenLink.current
    val openLinkSpecificBrowser = LocalOpenLinkSpecificBrowser.current
    val coroutineScope = rememberCoroutineScope()
    val summaryNavigationController = remember { SummaryNavigationController() }
    val minVisibleSummaryHeightPx = with(LocalDensity.current) { 24.dp.toPx() }
    val articleContent = readerState.content.text.orEmpty()
    val contentBlocks by
        produceState(
            initialValue = emptyList<ArticleContentBlock>(),
            key1 = articleContent,
            key2 = readerState.link,
        ) {
            value =
                if (articleContent.isBlank()) {
                    emptyList()
                } else {
                    withContext(Dispatchers.Default) {
                        ArticleContentBlockParser.parse(
                            content = articleContent,
                            baseUrl = readerState.link ?: "",
                        )
                    }
                }
        }
    val translatedBlockMap by
        produceState(
            initialValue = emptyMap<String, String>(),
            key1 = readingUiState.translatedContentBlocks,
        ) {
            value =
                withContext(Dispatchers.Default) {
                    parseTranslatedBlockMap(readingUiState.translatedContentBlocks)
                }
        }
    val translatedBlockIds = remember(translatedBlockMap) { translatedBlockMap.keys }

    var isReaderScrollingDown by remember { mutableStateOf(false) }
    var showFullScreenImageViewer by remember { mutableStateOf(false) }

    var currentImageData by remember { mutableStateOf(ImageData()) }
    var currentWebView by remember { mutableStateOf<WebView?>(null) }
    val clipboardManager = context.getSystemService(ClipboardManager::class.java)

    val isShowToolBar =
        if (LocalReadingAutoHideToolbar.current.value) {
            readerState.articleId != null && !isReaderScrollingDown
        } else {
            true
        }

    var showTopDivider by remember { mutableStateOf(false) }

    //    LaunchedEffect(readerState.listIndex) {
    //        readerState.listIndex?.let {
    //            navController.previousBackStackEntry?.savedStateHandle?.set("articleIndex", it)
    //        }
    //    }

    var bringToTop by remember { mutableStateOf(false) }
    var summaryReturnTarget by remember(readerState.articleId) { mutableStateOf<SummaryReturnTarget?>(null) }
    var latestReadingPosition by remember(readerState.articleId) { mutableStateOf<SummaryReturnTarget?>(null) }

    suspend fun captureSelectedSnippet(): String? =
        when (readingRenderer) {
            ReadingRendererPreference.WebView -> currentWebView?.captureSelectedText()
            ReadingRendererPreference.NativeComponent ->
                clipboardManager
                    ?.primaryClip
                    ?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)
                    ?.coerceToText(context)
                    ?.toString()
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
        }

    LaunchedEffect(
        readerState.articleId,
        readingUiState.articleWithFeed?.feed?.isAutoSummary,
        readingUiState.shouldAutoGenerateAiSummary,
    ) {
        if (
            readerState.articleId != null &&
                shouldAutoSummarize(
                    feedAutoSummary = readingUiState.articleWithFeed?.feed?.isAutoSummary == true,
                    state = readingUiState,
                )
        ) {
            viewModel.autoSummarizeCurrentArticle()
        }
    }

    LaunchedEffect(
        readerState.articleId,
        readerState.content.text,
        readingUiState.articleWithFeed?.feed?.isTranslationEnabled,
        readingUiState.articleWithFeed?.feed?.isAutoTranslate,
        readingUiState.shouldAutoGenerateTranslation,
    ) {
        if (
            readerState.articleId != null &&
                readingUiState.articleWithFeed?.feed?.isTranslationEnabled == true &&
                readingUiState.articleWithFeed?.feed?.isAutoTranslate == true &&
                readingUiState.shouldAutoGenerateTranslation
        ) {
            viewModel.autoTranslateCurrentArticle()
        }
    }

    LaunchedEffect(readingUiState.aiSummaryError, readingUiState.isAiSummaryVisible) {
        if (readingUiState.aiSummaryError != null && !readingUiState.isAiSummaryVisible) {
            context.showToast(readingUiState.aiSummaryError)
            viewModel.clearHiddenAiSummaryError()
        }
    }

    LaunchedEffect(readingUiState.translationError) {
        if (readingUiState.translationError != null) {
            context.showToast(readingUiState.translationError)
            viewModel.clearTranslationError()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        content = { paddings ->
            Box(modifier = Modifier.fillMaxSize()) {
                if (readerState.articleId != null) {
                    TopBar(
                        isShow = isShowToolBar,
                        isScrolled = showTopDivider,
                        title = readerState.title,
                        link = readerState.link,
                        onClick = { bringToTop = true },
                        navigationAction = navigationAction,
                        onNavButtonClick = onNavAction,
                        onNavigateToStylePage = onNavigateToStylePage,
                        isAiSummaryLoading = readingUiState.isAiSummaryLoading,
                        onAiSummaryClick = { coroutineScope.launch { viewModel.summarizeCurrentArticle() } },
                        isAiSummaryReady = readingUiState.shouldShowAiSummaryReadyPrompt,
                        isAiSummaryReturnAvailable = summaryReturnTarget != null,
                        onAiChatClick = {
                            coroutineScope.launch {
                                viewModel.openAiChatSheet(captureSelectedSnippet())
                            }
                        },
                        onAiSummaryReadyClick = {
                            summaryReturnTarget = latestReadingPosition
                            coroutineScope.launch {
                                summaryNavigationController.jumpToSummary?.invoke()
                                // Avoid expanding the summary card in the same frame as the
                                // programmatic scroll; LazyColumn can otherwise reuse stale item
                                // heights and briefly overlap the article body.
                                withFrameNanos { }
                                viewModel.showAiSummaryFromPrompt()
                            }
                        },
                        onAiSummaryReturnClick = {
                            summaryReturnTarget?.let { target ->
                                summaryNavigationController.restoreReturnTarget?.invoke(target)
                            }
                            summaryReturnTarget = null
                        },
                        isTranslationEnabled =
                            readingUiState.articleWithFeed?.feed?.isTranslationEnabled == true,
                        isTranslationLoading = readingUiState.isTranslationLoading,
                        onTranslateClick = {
                            coroutineScope.launch { viewModel.translateCurrentArticle() }
                        },
                    )
                }

                val isNextArticleAvailable = readerState.nextArticle != null
                val isPreviousArticleAvailable = readerState.previousArticle != null

                if (readerState.articleId != null) {
                    // Content
                    AnimatedContent(
                        targetState = readerState,
                        transitionSpec = {
                            val direction =
                                when {
                                    initialState.nextArticle?.articleId == targetState.articleId ->
                                        UPWARD
                                    initialState.previousArticle?.articleId ==
                                        targetState.articleId -> DOWNWARD
                                    initialState.articleId == targetState.articleId -> {
                                        when (targetState.content) {
                                            is ReaderState.Description -> DOWNWARD
                                            else -> UPWARD
                                        }
                                    }

                                    else -> UPWARD
                                }
                            val exit = 100
                            val enter = exit * 2
                            (slideInVertically(
                                initialOffsetY = { (it * 0.2f * direction).toInt() },
                                animationSpec =
                                    spring(
                                        dampingRatio = .9f,
                                        stiffness = Spring.StiffnessLow,
                                        visibilityThreshold = IntOffset.VisibilityThreshold,
                                    ),
                            ) +
                                fadeIn(
                                    tween(
                                        delayMillis = exit,
                                        durationMillis = enter,
                                        easing = LinearOutSlowInEasing,
                                    )
                                )) togetherWith
                                (slideOutVertically(
                                    targetOffsetY = { (it * -0.2f * direction).toInt() },
                                    animationSpec =
                                        spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessLow,
                                            visibilityThreshold = IntOffset.VisibilityThreshold,
                                        ),
                                ) +
                                    fadeOut(
                                        tween(durationMillis = exit, easing = FastOutLinearInEasing)
                                    ))
                        },
                        label = "",
                    ) {
                        remember { it }
                            .run {
                                val state =
                                    rememberPullToLoadState(
                                        key = content,
                                        onLoadNext =
                                            if (isNextArticleAvailable) {
                                                {
                                                    val (id, index) = readerState.nextArticle
                                                    onLoadArticle(id, index)
                                                }
                                            } else null,
                                        onLoadPrevious =
                                            if (isPreviousArticleAvailable) {
                                                {
                                                    val (id, index) = readerState.previousArticle
                                                    onLoadArticle(id, index)
                                                }
                                            } else null,
                                    )

                                val listState =
                                    rememberSaveable(
                                        inputs = arrayOf(content),
                                        saver = LazyListState.Saver,
                                    ) {
                                        LazyListState()
                                    }

                                val scrollState = rememberScrollState()

                                val scope = rememberCoroutineScope()

                                summaryNavigationController.jumpToSummary = {
                                    when (readingRenderer) {
                                        ReadingRendererPreference.WebView -> {
                                            if (scrollState.value != 0) {
                                                scrollState.animateScrollTo(0)
                                            }
                                        }

                                        ReadingRendererPreference.NativeComponent -> {
                                            if (
                                                listState.firstVisibleItemIndex != 0 ||
                                                    listState.firstVisibleItemScrollOffset != 0
                                            ) {
                                                listState.animateScrollToItem(0)
                                            }
                                        }
                                    }
                                }
                                summaryNavigationController.restoreReturnTarget = { target ->
                                    scope.launch {
                                        when (target) {
                                            is SummaryReturnTarget.Scroll -> {
                                                if (scrollState.value != target.value) {
                                                    scrollState.animateScrollTo(target.value)
                                                }
                                            }

                                            is SummaryReturnTarget.List -> {
                                                if (
                                                    listState.firstVisibleItemIndex != target.index ||
                                                        listState.firstVisibleItemScrollOffset !=
                                                            target.offset
                                                ) {
                                                    listState.animateScrollToItem(
                                                        target.index,
                                                        target.offset,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                LaunchedEffect(
                                    scrollState,
                                    readingRenderer,
                                    readerState.articleId,
                                    contentBlocks,
                                ) {
                                    if (readingRenderer == ReadingRendererPreference.WebView) {
                                        snapshotFlow { scrollState.value }
                                            .collect {
                                                latestReadingPosition = SummaryReturnTarget.Scroll(it)
                                                viewModel.updateTranslationFocusIndex(
                                                    estimateWebViewTranslationFocusIndex(
                                                        scrollValue = it,
                                                        maxScrollValue = scrollState.maxValue,
                                                        blocks = contentBlocks,
                                                    )
                                                )
                                            }
                                    }
                                }

                                LaunchedEffect(
                                    listState,
                                    readingRenderer,
                                    readerState.articleId,
                                    contentBlocks,
                                    translatedBlockIds,
                                ) {
                                    if (readingRenderer == ReadingRendererPreference.NativeComponent) {
                                        snapshotFlow {
                                            Triple(
                                                SummaryReturnTarget.List(
                                                    index = listState.firstVisibleItemIndex,
                                                    offset = listState.firstVisibleItemScrollOffset,
                                                ),
                                                estimateNativeTranslationFocusIndex(
                                                    firstVisibleItemIndex =
                                                        listState.firstVisibleItemIndex,
                                                    blocks = contentBlocks,
                                                    translatedBlockIds = translatedBlockIds,
                                                ),
                                                isNativeSummaryItemVisible(
                                                    layoutInfo = listState.layoutInfo,
                                                    minVisibleHeightPx = minVisibleSummaryHeightPx,
                                                ),
                                            )
                                        }.collect { (position, estimatedBlockIndex, isSummaryVisible) ->
                                            latestReadingPosition = position
                                            viewModel.updateTranslationFocusIndex(estimatedBlockIndex)
                                            viewModel.updateAiSummaryCardVisible(isSummaryVisible)
                                        }
                                    }
                                }

                                LaunchedEffect(bringToTop) {
                                    if (bringToTop) {
                                        scope
                                            .launch {
                                                if (scrollState.value != 0) {
                                                    scrollState.animateScrollTo(0)
                                                } else if (listState.firstVisibleItemIndex != 0) {
                                                    listState.animateScrollToItem(0)
                                                }
                                            }
                                            .invokeOnCompletion { bringToTop = false }
                                    }
                                }

                                showTopDivider =
                                    snapshotFlow {
                                            scrollState.value >= 120 ||
                                                listState.firstVisibleItemIndex != 0
                                        }
                                        .collectAsStateValue(initial = false)

                                CompositionLocalProvider(
                                    LocalTextStyle provides
                                        LocalTextStyle.current.run {
                                            merge(
                                                lineHeight =
                                                    if (lineHeight.isSpecified)
                                                        (lineHeight.value *
                                                                LocalReadingTextLineHeight.current)
                                                            .sp
                                                    else TextUnit.Unspecified
                                            )
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Content(
                                            modifier =
                                                Modifier.pullToLoad(
                                                    state = state,
                                                    onScroll = { f ->
                                                        if (abs(f) > 2f)
                                                            isReaderScrollingDown = f < 0f
                                                    },
                                                    enabled = isPullToSwitchArticleEnabled,
                                                ),
                                            contentPadding = paddings,
                                            content = content.text ?: "",
                                            rawDescription =
                                                readingUiState.articleWithFeed?.article?.rawDescription.orEmpty(),
                                            shortDescription =
                                                readingUiState.articleWithFeed?.article?.shortDescription.orEmpty(),
                                            aiSummary = readingUiState.aiSummary,
                                            isAiSummaryLoading = readingUiState.isAiSummaryLoading,
                                            aiSummaryError = readingUiState.aiSummaryError,
                                            isAiSummaryExpanded =
                                                readingUiState.isAiSummaryExpanded,
                                            translatedContentBlocks =
                                                readingUiState.translatedContentBlocks,
                                            contentBlocks = contentBlocks,
                                            translatedBlockMap = translatedBlockMap,
                                            feedName = feedName,
                                            title = title.toString(),
                                            author = author,
                                            link = link,
                                            publishedDate = publishedDate,
                                            isLoading = content is ReaderState.Loading,
                                            scrollState = scrollState,
                                            listState = listState,
                                            onImageClick = { imgUrl, altText ->
                                                currentImageData = ImageData(imgUrl, altText)
                                                showFullScreenImageViewer = true
                                            },
                                            onAiSummaryToggleExpand = {
                                                viewModel.toggleAiSummaryExpanded()
                                            },
                                            onAiSummaryVisibilityChanged = {
                                                viewModel.updateAiSummaryCardVisible(it)
                                            },
                                            onWebViewReady = { currentWebView = it },
                                        )
                                        PullToLoadIndicator(
                                            state = state,
                                            canLoadPrevious = isPreviousArticleAvailable,
                                            canLoadNext = isNextArticleAvailable,
                                        )
                                    }
                                }
                            }
                    }
                }
                // Bottom Bar
                if (readerState.articleId != null) {
                    BottomBar(
                        isShow = isShowToolBar,
                        isUnread = readingUiState.isUnread,
                        isStarred = readingUiState.isStarred,
                        isNextArticleAvailable = isNextArticleAvailable,
                        isFullContent =
                            readerState.content is ReaderState.FullContent ||
                                readerState.content is ReaderState.Error,
                        isBoldCharacters = boldCharacters.value,
                        onUnread = { viewModel.updateReadStatus(it) },
                        onStarred = { viewModel.updateStarredStatus(it) },
                        onNextArticle = {
                            readerState.nextArticle?.let {
                                val (id, index) = it
                                onLoadArticle(id, index)
                            }
                        },
                        onFullContent = {
                            if (it) viewModel.renderFullContent()
                            else viewModel.renderDescriptionContent()
                        },
                        onFullContentLongClick = {
                            context.openURL(
                                readerState.link,
                                openLink,
                                openLinkSpecificBrowser,
                            )
                        },
                        onBoldCharacters = { (!boldCharacters).put(context, coroutineScope) },
                        onReadAloud = {
                            viewModel.playCurrentArticleNow()
                        },
                        ttsButton = {
                            TtsButton(
                                onClick = {
                                    when (it) {
                                        TextToSpeechManager.State.Error -> {
                                            context.showToast("TextToSpeech initialization failed")
                                        }

                                        TextToSpeechManager.State.Idle -> {
                                            viewModel.playCurrentArticleNow()
                                        }

                                        is TextToSpeechManager.State.Reading -> {
                                            viewModel.stopQueuePlayback()
                                        }

                                        TextToSpeechManager.State.Preparing -> {
                                            /* no-op */
                                        }
                                    }
                                },
                                onLongClick = {
                                    viewModel.addCurrentArticleToPlaylist()
                                    onOpenQueue()
                                },
                                state =
                                    viewModel.textToSpeechManager.stateFlow.collectAsStateValue(),
                            )
                        },
                    )
                }
            }
        },
    )
    if (readingUiState.isAiChatSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.closeAiChatSheet() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            AiChatSheet(
                messages = readingUiState.aiChatMessages,
                includeFullContent = readingUiState.includeFullContentInAiChat,
                selectedSnippet = readingUiState.aiChatSelectedSnippet,
                isSending = readingUiState.isAiChatSending,
                error = readingUiState.aiChatError,
                onIncludeFullContentChange = viewModel::updateAiChatIncludeFullContent,
                onQuickAction = viewModel::sendAiChatQuickAction,
                onSendMessage = viewModel::sendAiChatMessage,
                onClearHistory = viewModel::clearAiChatHistory,
                onClearSelectedSnippet = viewModel::clearAiChatSelectedSnippet,
                onClose = viewModel::closeAiChatSheet,
            )
        }
    }
    if (showFullScreenImageViewer) {

        ReaderImageViewer(
            imageData = currentImageData,
            onDownloadImage = {
                viewModel.downloadImage(
                    it,
                    onSuccess = { context.showToast(context.getString(R.string.image_saved)) },
                    onFailure = {
                        // FIXME: crash the app for error report
                        th ->
                        throw th
                    },
                )
            },
            onDismissRequest = { showFullScreenImageViewer = false },
        )
    }
}
