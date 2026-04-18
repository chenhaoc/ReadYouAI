package me.ash.reader.ui.page.home.reading

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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlinx.coroutines.launch
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

private sealed interface SummaryReturnTarget {
    data class Scroll(val value: Int) : SummaryReturnTarget
    data class List(val index: Int, val offset: Int) : SummaryReturnTarget
}

private class SummaryNavigationController {
    var jumpToSummary: (() -> Unit)? = null
    var restoreReturnTarget: ((SummaryReturnTarget) -> Unit)? = null
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
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
    val articleContent = readerState.content.text.orEmpty()
    val contentBlocks =
        remember(articleContent, readerState.link) {
            ArticleContentBlockParser.parse(
                content = articleContent,
                baseUrl = readerState.link ?: "",
            )
        }
    val translatedBlockIds =
        remember(readingUiState.translatedContentBlocks) {
            parseTranslatedBlockMap(readingUiState.translatedContentBlocks).keys
        }

    var isReaderScrollingDown by remember { mutableStateOf(false) }
    var showFullScreenImageViewer by remember { mutableStateOf(false) }

    var currentImageData by remember { mutableStateOf(ImageData()) }

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
                        onAiSummaryReadyClick = {
                            summaryReturnTarget = latestReadingPosition
                            viewModel.showAiSummaryFromPrompt()
                            summaryNavigationController.jumpToSummary?.invoke()
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
                                    scope.launch {
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

                                LaunchedEffect(scrollState, readingRenderer, readerState.articleId) {
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

                                LaunchedEffect(listState, readingRenderer, readerState.articleId) {
                                    if (readingRenderer == ReadingRendererPreference.NativeComponent) {
                                        snapshotFlow {
                                            SummaryReturnTarget.List(
                                                index = listState.firstVisibleItemIndex,
                                                offset = listState.firstVisibleItemScrollOffset,
                                            )
                                        }.collect {
                                            latestReadingPosition = it
                                            val estimatedBlockIndex =
                                                estimateNativeTranslationFocusIndex(
                                                    firstVisibleItemIndex = listState.firstVisibleItemIndex,
                                                    blocks = contentBlocks,
                                                    translatedBlockIds = translatedBlockIds,
                                                )
                                            viewModel.updateTranslationFocusIndex(estimatedBlockIndex)
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
                                            aiSummary = readingUiState.aiSummary,
                                            isAiSummaryLoading = readingUiState.isAiSummaryLoading,
                                            aiSummaryError = readingUiState.aiSummaryError,
                                            isAiSummaryExpanded =
                                                readingUiState.isAiSummaryExpanded,
                                            translatedContentBlocks =
                                                readingUiState.translatedContentBlocks,
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
