package me.ash.reader.ui.page.nav3

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.datastore.preferences.core.edit
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.ash.reader.infrastructure.android.ttsqueue.TtsCommuteQueueGenerationMode
import me.ash.reader.infrastructure.preference.LocalSettings
import me.ash.reader.infrastructure.preference.LocalReadingTtsMiniPlayer
import me.ash.reader.ui.ext.PreferencesKey
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.showToast
import me.ash.reader.ui.motion.materialSharedAxisXIn
import me.ash.reader.ui.motion.materialSharedAxisXOut
import me.ash.reader.ui.page.adaptive.ArticleData
import me.ash.reader.ui.page.adaptive.ArticleListReaderPage
import me.ash.reader.ui.page.adaptive.ArticleListReaderViewModel
import me.ash.reader.ui.page.home.feeds.FeedsPage
import me.ash.reader.ui.page.home.feeds.subscribe.SubscribeViewModel
import me.ash.reader.ui.page.home.reading.queue.TtsFloatingButtonDockSide
import me.ash.reader.ui.page.home.reading.queue.TtsFloatingPlayerButton
import me.ash.reader.ui.page.home.reading.queue.TtsQueueOverlayViewModel
import me.ash.reader.ui.page.home.reading.queue.TtsQueueSheet
import me.ash.reader.ui.page.nav3.key.Route
import me.ash.reader.ui.page.settings.SettingsPage
import me.ash.reader.ui.page.settings.accounts.AccountDetailsPage
import me.ash.reader.ui.page.settings.accounts.AccountViewModel
import me.ash.reader.ui.page.settings.accounts.AccountsPage
import me.ash.reader.ui.page.settings.accounts.AddAccountsPage
import me.ash.reader.ui.page.settings.ai.AiPresetManagerPage
import me.ash.reader.ui.page.settings.ai.AiSettingsPage
import me.ash.reader.ui.page.settings.backuprestore.BackupRestorePage
import me.ash.reader.ui.page.settings.color.ColorAndStylePage
import me.ash.reader.ui.page.settings.color.DarkThemePage
import me.ash.reader.ui.page.settings.color.feeds.FeedsPageStylePage
import me.ash.reader.ui.page.settings.color.flow.FlowPageStylePage
import me.ash.reader.ui.page.settings.color.reading.BoldCharactersPage
import me.ash.reader.ui.page.settings.color.reading.ReadingImagePage
import me.ash.reader.ui.page.settings.color.reading.ReadingStylePage
import me.ash.reader.ui.page.settings.color.reading.ReadingTextPage
import me.ash.reader.ui.page.settings.color.reading.ReadingTitlePage
import me.ash.reader.ui.page.settings.color.reading.ReadingVideoPage
import me.ash.reader.ui.page.settings.interaction.InteractionPage
import me.ash.reader.ui.page.settings.languages.LanguagesPage
import me.ash.reader.ui.page.settings.tips.LicenseListPage
import me.ash.reader.ui.page.settings.tips.TipsAndSupportPage
import me.ash.reader.ui.page.settings.troubleshooting.TroubleshootingPage
import me.ash.reader.ui.page.startup.StartupPage

private const val INITIAL_OFFSET_FACTOR = 0.10f
private val readingTtsMiniPlayerDockSideKey =
    PreferencesKey.StringKey(PreferencesKey.readingTtsMiniPlayerDockSide).key
private val readingTtsMiniPlayerVerticalRatioKey =
    PreferencesKey.FloatKey(PreferencesKey.readingTtsMiniPlayerVerticalRatio).key

@OptIn(
    ExperimentalSharedTransitionApi::class,
    ExperimentalMaterial3AdaptiveApi::class,
    ExperimentalMaterial3Api::class,
)
@Composable
fun AppEntry(backStack: NavBackStack<NavKey>) {
    val subscribeViewModel = hiltViewModel<SubscribeViewModel>()
    val overlayViewModel = hiltViewModel<TtsQueueOverlayViewModel>()
    val context = LocalContext.current
    val settings = LocalSettings.current
    val queueState = overlayViewModel.queueState.collectAsStateValue()
    val commuteBuildResult = overlayViewModel.commuteBuildResult.collectAsStateValue()
    val commuteBuildGenerationMode = overlayViewModel.commuteBuildGenerationMode.collectAsStateValue()
    val showFloatingButton = LocalReadingTtsMiniPlayer.current
    val scope = rememberCoroutineScope()
    val queueSheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
        )
    var isQueueSheetMounted by rememberSaveable { mutableStateOf(false) }
    val isQueueDrawerVisible =
        isQueueSheetMounted ||
            queueSheetState.currentValue != SheetValue.Hidden ||
            queueSheetState.targetValue != SheetValue.Hidden

    LaunchedEffect(commuteBuildResult) {
        val result = commuteBuildResult ?: return@LaunchedEffect
        val message =
            when {
                !result.hasSources -> context.getString(me.ash.reader.R.string.commute_brief_no_source)
                result.items.isEmpty() -> context.getString(me.ash.reader.R.string.commute_brief_no_items)
                result.aiRecommendationFallback ->
                    context.getString(me.ash.reader.R.string.commute_brief_ai_fallback)
                result.generationMode == TtsCommuteQueueGenerationMode.AiRecommended ->
                    context.getString(
                        me.ash.reader.R.string.commute_brief_ai_generated,
                        result.estimatedDurationMinutes,
                    )
                result.estimatedDurationMinutes < settings.commuteBriefDuration.minutes ->
                    context.getString(
                        me.ash.reader.R.string.commute_brief_generated_short,
                        result.estimatedDurationMinutes,
                    )
                else ->
                    context.getString(
                        me.ash.reader.R.string.commute_brief_generated,
                        result.estimatedDurationMinutes,
                    )
            }
        context.showToast(message)
        overlayViewModel.clearCommuteBuildResult()
    }
    val dockSide =
        runCatching {
            TtsFloatingButtonDockSide.valueOf(settings.readingTtsMiniPlayerDockSide)
        }.getOrDefault(TtsFloatingButtonDockSide.Right)
    val currentRoute = backStack.lastOrNull()

    val onBack: () -> Unit = {
        if (backStack.size == 1) backStack[0] = Route.Feeds else backStack.removeLastOrNull()
    }
    val openQueue: () -> Unit = {
        isQueueSheetMounted = true
        scope.launch { queueSheetState.show() }
    }
    fun closeQueue(afterClose: (() -> Unit)? = null) {
        scope.launch {
            queueSheetState.hide()
            isQueueSheetMounted = false
            afterClose?.invoke()
        }
    }

    val scaffoldDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo())

    val navigator =
        rememberListDetailPaneScaffoldNavigator<ArticleData>(
            scaffoldDirective = scaffoldDirective,
            isDestinationHistoryAware = false,
        )

    SharedTransitionLayout {
        NavDisplay(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
            backStack = backStack,
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
            transitionSpec = {
                materialSharedAxisXIn(
                    initialOffsetX = { (it * INITIAL_OFFSET_FACTOR).toInt() }
                ) togetherWith
                    materialSharedAxisXOut(
                        targetOffsetX = { -(it * INITIAL_OFFSET_FACTOR).toInt() }
                    )
            },
            popTransitionSpec = {
                materialSharedAxisXIn(
                    initialOffsetX = { -(it * INITIAL_OFFSET_FACTOR).toInt() }
                ) togetherWith
                    materialSharedAxisXOut(targetOffsetX = { (it * INITIAL_OFFSET_FACTOR).toInt() })
            },
            predictivePopTransitionSpec = {
                materialSharedAxisXIn(
                    initialOffsetX = { -(it * INITIAL_OFFSET_FACTOR).toInt() }
                ) togetherWith
                    materialSharedAxisXOut(targetOffsetX = { (it * INITIAL_OFFSET_FACTOR).toInt() })
            },
            onBack = onBack,
            entryProvider = { key ->
                when (key) {
                        Route.Feeds -> {
                            NavEntry(key) {
                                FeedsPage(
                                    subscribeViewModel = subscribeViewModel,
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                                    navigateToSettings = { backStack.add(Route.Settings) },
                                    navigationToFlow = { articleId ->
                                        backStack.add(Route.Reading(articleId))
                                    },
                                    onOpenQueue = openQueue,
                                    isQueueOpen = isQueueDrawerVisible,
                                    navigateToAccountList = { backStack.add(Route.Accounts) },
                                    navigateToAccountDetail = {
                                        backStack.add(Route.AccountDetails(it))
                                    },
                                )
                            }
                        }

                        is Route.Reading -> {
                            NavEntry(key) {
                                val route = rememberSaveable(saver = Route.Reading.Saver) { key }

                                LaunchedEffect(route) {
                                    if (route.articleId != null) {
                                        delay(50L)
                                        navigator.navigateTo(
                                            ListDetailPaneScaffoldRole.Detail,
                                            ArticleData(route.articleId),
                                        )
                                    }
                                }

                                val viewModel = hiltViewModel<ArticleListReaderViewModel>()

                                ArticleListReaderPage(
                                    scaffoldDirective = scaffoldDirective,
                                    navigator = navigator,
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                                    viewModel = viewModel,
                                    onOpenQueue = openQueue,
                                    isQueueOpen = isQueueDrawerVisible,
                                    onBack = onBack,
                                    onNavigateToStylePage = {
                                        backStack.add(Route.ReadingPageStyle)
                                    },
                                )
                            }
                        }

                        Route.Startup -> {
                            NavEntry(key) {
                                StartupPage(onNavigateToFeeds = { backStack.add(Route.Feeds) })
                            }
                        }

                        Route.Settings ->
                            NavEntry(key) {
                                SettingsPage(
                                    onBack = onBack,
                                    navigateToAccounts = { backStack.add(Route.Accounts) },
                                    navigateToColorAndStyle = { backStack.add(Route.ColorAndStyle) },
                                    navigateToInteraction = { backStack.add(Route.Interaction) },
                                    navigateToAiSettings = { backStack.add(Route.AiSettings) },
                                    navigateToBackupRestore = {
                                        backStack.add(Route.BackupRestore)
                                    },
                                    navigateToLanguages = { backStack.add(Route.Languages) },
                                    navigateToTroubleshooting = {
                                        backStack.add(Route.Troubleshooting)
                                    },
                                    navigateToTipsAndSupport = {
                                        backStack.add(Route.TipsAndSupport)
                                    },
                                )
                            }

                        Route.Accounts ->
                            NavEntry(key) {
                                AccountsPage(
                                    onBack = onBack,
                                    navigateToAddAccount = { backStack.add(Route.AddAccounts) },
                                    navigateToAccountDetails = {
                                        backStack.add(Route.AccountDetails(it))
                                    },
                                )
                            }

                        is Route.AccountDetails ->
                            NavEntry(key) {
                                AccountDetailsPage(
                                    viewModel =
                                        hiltViewModel<AccountViewModel>().also {
                                            it.initData(key.accountId)
                                        },
                                    onBack = onBack,
                                    navigateToFeeds = { backStack.add(Route.Feeds) },
                                )
                            }

                        Route.AddAccounts ->
                            NavEntry(key) {
                                AddAccountsPage(
                                    onBack = onBack,
                                    navigateToAccountDetails = {
                                        backStack.add(Route.AccountDetails(it))
                                    },
                                )
                            }

                        Route.ColorAndStyle ->
                            NavEntry(key) {
                                ColorAndStylePage(
                                    onBack = onBack,
                                    navigateToDarkTheme = { backStack.add(Route.DarkTheme) },
                                    navigateToFeedsPageStyle = {
                                        backStack.add(Route.FeedsPageStyle)
                                    },
                                    navigateToFlowPageStyle = {
                                        backStack.add(Route.FlowPageStyle)
                                    },
                                    navigateToReadingPageStyle = {
                                        backStack.add(Route.ReadingPageStyle)
                                    },
                                )
                            }

                        Route.DarkTheme -> NavEntry(key) { DarkThemePage(onBack = onBack) }
                        Route.FeedsPageStyle ->
                            NavEntry(key) { FeedsPageStylePage(onBack = onBack) }
                        Route.FlowPageStyle ->
                            NavEntry(key) { FlowPageStylePage(onBack = onBack) }
                        Route.ReadingPageStyle ->
                            NavEntry(key) {
                                ReadingStylePage(
                                    onBack = onBack,
                                    navigateToReadingBoldCharacters = {
                                        backStack.add(Route.ReadingBoldCharacters)
                                    },
                                    navigateToReadingPageTitle = {
                                        backStack.add(Route.ReadingPageTitle)
                                    },
                                    navigateToReadingPageText = {
                                        backStack.add(Route.ReadingPageText)
                                    },
                                    navigateToReadingPageImage = {
                                        backStack.add(Route.ReadingPageImage)
                                    },
                                    navigateToReadingPageVideo = {
                                        backStack.add(Route.ReadingPageVideo)
                                    },
                                )
                            }

                        Route.ReadingBoldCharacters ->
                            NavEntry(key) { BoldCharactersPage(onBack = onBack) }
                        Route.ReadingPageTitle ->
                            NavEntry(key) { ReadingTitlePage(onBack = onBack) }
                        Route.ReadingPageText ->
                            NavEntry(key) { ReadingTextPage(onBack = onBack) }
                        Route.ReadingPageImage ->
                            NavEntry(key) { ReadingImagePage(onBack = onBack) }
                        Route.ReadingPageVideo ->
                            NavEntry(key) { ReadingVideoPage(onBack = onBack) }
                        Route.Interaction -> NavEntry(key) { InteractionPage(onBack = onBack) }
                        Route.AiSettings -> NavEntry(key) {
                            AiSettingsPage(
                                onBack = onBack,
                                navigateToPresetManager = { backStack.add(Route.AiPresetManager) },
                            )
                        }

                        Route.AiPresetManager -> NavEntry(key) {
                            AiPresetManagerPage(onBack = onBack)
                        }
                        Route.BackupRestore ->
                            NavEntry(key) { BackupRestorePage(onBack = onBack) }
                        Route.Languages -> NavEntry(key) { LanguagesPage(onBack = onBack) }
                        Route.Troubleshooting ->
                            NavEntry(key) { TroubleshootingPage(onBack = onBack) }
                        Route.TipsAndSupport ->
                            NavEntry(key) {
                                TipsAndSupportPage(
                                    onBack = onBack,
                                    navigateToLicenseList = {
                                        backStack.add(Route.LicenseList)
                                    },
                                )
                            }

                        Route.LicenseList ->
                            NavEntry(key) { LicenseListPage(onBack = onBack) }
                        else -> NavEntry(key) { throw Exception("Unknown destination") }
                }
            },
        )

        TtsFloatingPlayerButton(
            visible =
                showFloatingButton.value &&
                    currentRoute != Route.Startup,
            dockSide = dockSide,
            verticalRatio = settings.readingTtsMiniPlayerVerticalRatio,
            bottomPadding = 0.dp,
            onPositionChange = { side, verticalRatio ->
                scope.launch {
                    context.dataStore.edit {
                        it[readingTtsMiniPlayerDockSideKey] = side.name
                        it[readingTtsMiniPlayerVerticalRatioKey] = verticalRatio
                    }
                }
            },
            onClick = openQueue,
            onLongClick = overlayViewModel::toggleQueuePlayback,
        )

        if (isQueueSheetMounted) {
            ModalBottomSheet(
                onDismissRequest = { closeQueue() },
                sheetState = queueSheetState,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .safeDrawingPadding(),
                ) {
                    TtsQueueSheet(
                        state = queueState,
                        commuteBuildGenerationMode = commuteBuildGenerationMode,
                        onSwitchMode = overlayViewModel::switchMode,
                        onGenerateCommuteBrief = overlayViewModel::generateCommuteBrief,
                        onPlayItem = overlayViewModel::playPlaylistItem,
                        onPauseCurrent = overlayViewModel::stopQueuePlayback,
                        onSeekCurrent = overlayViewModel::seekCurrentPlayback,
                        onPreviousSegment = overlayViewModel::previousQueueSegment,
                        onNextSegment = overlayViewModel::nextQueueSegment,
                        onSetSleepTimer = overlayViewModel::setSleepTimer,
                        onToggleCurrentStarred = overlayViewModel::toggleCurrentStarred,
                        onOpenCurrentArticle = { articleId ->
                            closeQueue {
                                scope.launch {
                                    if (backStack.lastOrNull() is Route.Reading) {
                                        navigator.navigateTo(
                                            pane = ListDetailPaneScaffoldRole.Detail,
                                            contentKey = ArticleData(articleId = articleId),
                                        )
                                    } else {
                                        backStack.add(Route.Reading(articleId))
                                    }
                                }
                            }
                        },
                        onPrevious = overlayViewModel::previousQueuePlayback,
                        onNext = overlayViewModel::skipQueuePlayback,
                        onRemove = overlayViewModel::removeFromPlaylist,
                        onMoveUp = overlayViewModel::movePlaylistItemUp,
                        onMoveDown = overlayViewModel::movePlaylistItemDown,
                        onClear = overlayViewModel::clearPlaylist,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
        }
    }
}
