package me.ash.reader.ui.page.adaptive

import android.net.Uri
import androidx.compose.ui.util.fastFirstOrNull
import com.google.gson.Gson
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Date
import javax.inject.Inject
import kotlin.collections.any
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.ash.reader.domain.data.ArticlePagingListUseCase
import me.ash.reader.domain.data.DiffMapHolder
import me.ash.reader.domain.data.FilterState
import me.ash.reader.domain.data.FilterStateUseCase
import me.ash.reader.domain.data.GroupWithFeedsListUseCase
import me.ash.reader.domain.data.PagerData
import me.ash.reader.domain.model.article.Article
import me.ash.reader.domain.model.article.ArticleFlowItem
import me.ash.reader.domain.model.article.ArticleWithFeed
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.model.general.MarkAsReadConditions
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.repository.AiSummaryRepository
import me.ash.reader.domain.repository.AiTranslationRepository
import me.ash.reader.domain.service.GoogleReaderRssService
import me.ash.reader.domain.service.LocalRssService
import me.ash.reader.domain.service.RssService
import me.ash.reader.domain.service.SyncWorker
import me.ash.reader.infrastructure.android.AndroidImageDownloader
import me.ash.reader.infrastructure.android.TextToSpeechManager
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.preference.PullToLoadNextFeedPreference
import me.ash.reader.infrastructure.preference.SettingsProvider
import me.ash.reader.infrastructure.rss.ReaderCacheHelper
import me.ash.reader.ui.page.home.flow.buildListTranslationSourceBlocks
import me.ash.reader.ui.page.home.reading.ArticleContentBlockParser
import me.ash.reader.ui.page.home.reading.buildPrioritizedTranslationBatch
import me.ash.reader.ui.page.home.reading.decodeStoredTranslationBlocks
import me.ash.reader.ui.page.home.reading.selectExtraTranslations
import me.ash.reader.ui.page.home.reading.selectTranslationsForCurrentBlocks
import me.ash.reader.ui.page.home.reading.translatableBlockCount
import me.ash.reader.ui.page.home.reading.translatedBlockCount
import timber.log.Timber

private const val TAG = "FlowViewModel"
private const val DEFAULT_TRANSLATION_PROMPT =
    "Translate the input JSON array into Simplified Chinese. Return JSON only. Preserve every id, keep the original order, do not summarize, do not omit content, and set translatedText for each item.\n\n"
private const val MAX_LIST_TRANSLATION_CONCURRENCY = 5

private enum class SummaryTrigger {
    MANUAL,
    AUTO,
}

private enum class TranslationTrigger {
    MANUAL,
    AUTO,
}

private data class TranslationContentState(
    val payload: String?,
    val translatedBlockCount: Int,
    val translatableBlockCount: Int,
)

@OptIn(FlowPreview::class)
@HiltViewModel()
class ArticleListReaderViewModel
@Inject
constructor(
    private val rssService: RssService,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope private val applicationScope: CoroutineScope,
    val diffMapHolder: DiffMapHolder,
    private val filterStateUseCase: FilterStateUseCase,
    private val groupWithFeedsListUseCase: GroupWithFeedsListUseCase,
    private val settingsProvider: SettingsProvider,
    private val readerCacheHelper: ReaderCacheHelper,
    val textToSpeechManager: TextToSpeechManager,
    private val imageDownloader: AndroidImageDownloader,
    private val articleListUseCase: ArticlePagingListUseCase,
    private val articleDao: ArticleDao,
    private val aiSummaryRepository: AiSummaryRepository,
    private val aiTranslationRepository: AiTranslationRepository,
    workManager: WorkManager,
) : ViewModel() {

    val flowUiState: StateFlow<FlowUiState?> =
        articleListUseCase.pagerFlow
            .combine(groupWithFeedsListUseCase.groupWithFeedListFlow) {
                pagerData,
                groupWithFeedsList ->
                val filterState = pagerData.filterState
                var nextFilterState: FilterState? = null
                if (filterState.group != null) {
                    val groupList = groupWithFeedsList.map { it.group }
                    val index = groupList.indexOfFirst { it.id == filterState.group.id }
                    if (index != -1) {
                        val nextGroup = groupList.getOrNull(index + 1)
                        if (nextGroup != null) {
                            nextFilterState = filterState.copy(group = nextGroup)
                        }
                    } else {
                        val allGroupList =
                            rssService.get().queryAllGroupWithFeeds().map { it.group }
                        val index = allGroupList.indexOfFirst { it.id == filterState.group.id }
                        if (index != -1) {
                            val nextGroup =
                                allGroupList.subList(index, allGroupList.size).fastFirstOrNull {
                                    groupList.map { it.id }.contains(it.id)
                                }
                            if (nextGroup != null) {
                                nextFilterState = filterState.copy(group = nextGroup)
                            }
                        }
                    }
                } else if (filterState.feed != null) {
                    val feedList = groupWithFeedsList.flatMap { it.feeds }
                    val index = feedList.indexOfFirst { it.id == filterState.feed.id }
                    if (index != -1) {
                        val nextFeed = feedList.getOrNull(index + 1)
                        if (nextFeed != null) {
                            nextFilterState = filterState.copy(feed = nextFeed)
                        }
                    } else {
                        val allFeedList =
                            rssService.get().queryAllGroupWithFeeds().flatMap { it.feeds }
                        val index = allFeedList.indexOfFirst { it.id == filterState.feed.id }
                        if (index != -1) {
                            val nextFeed =
                                allFeedList.subList(index, allFeedList.size).fastFirstOrNull {
                                    feedList.map { it.id }.contains(it.id)
                                }
                            if (nextFeed != null) {
                                nextFilterState = filterState.copy(feed = nextFeed)
                            }
                        }
                    }
                }
                FlowUiState(nextFilterState = nextFilterState, pagerData = pagerData)
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val syncWorkerStatusFlow =
        workManager
            .getWorkInfosByTagFlow(SyncWorker.SYNC_TAG)
            .map { it.any { workInfo -> workInfo.state == WorkInfo.State.RUNNING } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _isSyncingFlow = MutableStateFlow(false)
    val isSyncingFlow = _isSyncingFlow.asStateFlow()

    init {
        viewModelScope.launch {
            syncWorkerStatusFlow.debounce(500L).collect { _isSyncingFlow.value = it }
        }
    }

    fun updateReadStatus(
        groupId: String?,
        feedId: String?,
        articleId: String?,
        conditions: MarkAsReadConditions,
        isUnread: Boolean,
    ) {
        applicationScope.launch(ioDispatcher) {
            rssService
                .get()
                .markAsRead(
                    groupId = groupId,
                    feedId = feedId,
                    articleId = articleId,
                    before = conditions.toDate(),
                    isUnread = isUnread,
                )
        }
    }

    fun updateStarredStatus(articleId: String?, isStarred: Boolean) {
        applicationScope.launch(ioDispatcher) {
            if (articleId != null) {
                rssService.get().markAsStarred(articleId = articleId, isStarred = isStarred)
            }
        }
    }

    fun markAsReadFromListByDate(date: Date, isBefore: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            val items =
                articleListUseCase.itemSnapshotList
                    .filterIsInstance<ArticleFlowItem.Article>()
                    .map { it.articleWithFeed }
                    .filter {
                        if (isBefore) {
                            date > it.article.date && it.article.isUnread
                        } else {
                            date < it.article.date && it.article.isUnread
                        }
                    }
                    .distinctBy { it.article.id }

            diffMapHolder.updateDiff(articleWithFeed = items.toTypedArray(), isUnread = false)
        }
    }

    fun loadNextFeedOrGroup() {
        viewModelScope.launch {
            if (
                settingsProvider.settings.pullToSwitchFeed ==
                    PullToLoadNextFeedPreference.MarkAsReadAndLoadNextFeed
            ) {
                markAllAsRead()
            }
            flowUiState.value?.nextFilterState?.let { filterStateUseCase.updateFilterState(it) }
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            val items =
                articleListUseCase.itemSnapshotList.items
                    .filterIsInstance<ArticleFlowItem.Article>()
                    .map { it.articleWithFeed }

            diffMapHolder.updateDiff(articleWithFeed = items.toTypedArray(), isUnread = false)
        }
    }

    fun sync() {
        diffMapHolder.commitDiffsToDb()
        viewModelScope.launch {
            _isSyncingFlow.value = true
            val isSyncing = syncWorkerStatusFlow.value
            if (!isSyncing) {
                delay(1000L)
                if (syncWorkerStatusFlow.value == false) {
                    _isSyncingFlow.value = false
                }
            }
        }
        applicationScope.launch(ioDispatcher) {
            val filterState = filterStateUseCase.filterStateFlow.value
            val service = rssService.get()
            when (service) {
                is LocalRssService ->
                    service.doSyncOneTime(
                        feedId = filterState.feed?.id,
                        groupId = filterState.group?.id,
                    )

                is GoogleReaderRssService ->
                    service.doSyncOneTime(
                        feedId = filterState.feed?.id,
                        groupId = filterState.group?.id,
                    )

                else -> service.doSyncOneTime()
            }
        }
    }

    fun resetFilter() =
        filterStateUseCase.updateFilterState(feed = null, group = null, searchContent = null)

    fun changeFilter(filterState: FilterState) {
        filterStateUseCase.updateFilterState(
            filterState.feed,
            filterState.group,
            filterState.filter,
        )
    }

    fun inputSearchContent(content: String? = null) {
        if (content != filterStateUseCase.filterStateFlow.value.searchContent)
            filterStateUseCase.updateFilterState(searchContent = content)
    }

    private val _readingUiState = MutableStateFlow(ReadingUiState())
    val readingUiState: StateFlow<ReadingUiState> = _readingUiState.asStateFlow()

    private val _readerState: MutableStateFlow<ReaderState> = MutableStateFlow(ReaderState())
    val readerStateStateFlow = _readerState.asStateFlow()
    private val isAiSummaryCardVisible = MutableStateFlow(true)
    private val translationFocusIndex = MutableStateFlow(0)
    private var translationJob: Job? = null
    private val pendingListTranslationArticleIds = linkedSetOf<String>()
    private val listTranslationJobs = mutableMapOf<String, Job>()
    private val activeListTranslationArticleIds = mutableSetOf<String>()

    private val currentArticle: Article?
        get() = readingUiState.value.articleWithFeed?.article

    private val currentFeed: Feed?
        get() = readingUiState.value.articleWithFeed?.feed

    fun initData(articleId: String, listIndex: Int? = null) {
        cancelTranslationJob()
        viewModelScope.launch {
            val snapshotList = articleListUseCase.itemSnapshotList

            val itemByIndex =
                listIndex?.let { snapshotList.getOrNull(it) as? ArticleFlowItem.Article }

            val itemFromList =
                if (itemByIndex != null && itemByIndex.articleWithFeed.article.id != articleId) {
                    itemByIndex
                } else {
                    snapshotList.find { item ->
                        item is ArticleFlowItem.Article &&
                            item.articleWithFeed.article.id == articleId
                    } as? ArticleFlowItem.Article
                }

            val item =
                rssService.get().findArticleById(articleId)
                    ?: itemByIndex?.articleWithFeed
                    ?: itemFromList?.articleWithFeed
                    ?: error("Article $articleId not found")

            if (diffMapHolder.checkIfUnread(item)) {
                diffMapHolder.updateDiff(item, isUnread = false)
            }
            item.run {
                _readingUiState.update {
                    it.copy(
                        articleWithFeed = this,
                        isStarred = article.isStarred,
                        isUnread = false,
                        aiSummary = article.aiSummary,
                        isAiSummaryLoading = false,
                        isAiSummaryInlineLoading = false,
                        aiSummaryError = null,
                        isAiSummaryExpanded = article.aiSummary != null,
                        shouldRenderAiSummaryInline = article.aiSummary != null,
                        shouldShowAiSummaryReadyPrompt = false,
                        hasAutoAiSummaryAttempted = false,
                        translatedContentBlocks = null,
                        isTranslationLoading = false,
                        isTranslationInlineLoading = false,
                        translationError = null,
                        shouldRenderTranslationInline = false,
                        hasAutoTranslationAttempted = false,
                        translatedBlockCount = 0,
                        translatableBlockCount = 0,
                    )
                }
                _readerState.update {
                    it.copy(
                            articleId = article.id,
                            feedName = feed.name,
                            title = article.title,
                            author = article.author,
                            link = article.link,
                            publishedDate = article.date,
                        )
                        .prefetchArticleId()
                        .renderContent(this)
                }
                syncTranslationStateForContent(_readerState.value.content.text ?: article.rawDescription)
            }
        }
    }

    fun clearReadingData() {
        cancelTranslationJob()
        _readingUiState.update { ReadingUiState() }
        _readerState.update { ReaderState() }
    }

    suspend fun ReaderState.renderContent(articleWithFeed: ArticleWithFeed): ReaderState {
        val contentState =
            if (articleWithFeed.feed.isFullContent) {
                val fullContent =
                    readerCacheHelper.readFullContent(articleWithFeed.article.id).getOrNull()
                if (fullContent != null) ReaderState.FullContent(fullContent)
                else {
                    renderFullContent()
                    ReaderState.Loading
                }
            } else ReaderState.Description(articleWithFeed.article.rawDescription)

        return copy(content = contentState)
    }

    fun renderDescriptionContent() {
        val content = currentArticle?.rawDescription ?: ""
        _readerState.update {
            it.copy(content = ReaderState.Description(content = content))
        }
        syncTranslationStateForContent(content)
    }

    fun renderFullContent() {
        val fetchJob =
            viewModelScope.launch {
                readerCacheHelper
                    .readOrFetchFullContent(currentArticle!!)
                    .onSuccess { content ->
                        _readerState.update {
                            it.copy(content = ReaderState.FullContent(content = content))
                        }
                        syncTranslationStateForContent(content)
                    }
                    .onFailure { th ->
                        _readerState.update {
                            it.copy(content = ReaderState.Error(th.message.toString()))
                        }
                    }
            }
        viewModelScope.launch {
            delay(100L)
            if (fetchJob.isActive) {
                setLoading()
            }
        }
    }

    fun updateReadStatus(isUnread: Boolean) {
        readingUiState.value.articleWithFeed?.let {
            diffMapHolder.updateDiff(it, isUnread = isUnread)
        }
        _readingUiState.update {
            it.copy(isUnread = diffMapHolder.checkIfUnread(it.articleWithFeed!!))
        }
    }

    fun updateStarredStatus(isStarred: Boolean) {
        applicationScope.launch(ioDispatcher) {
            _readingUiState.update { it.copy(isStarred = isStarred) }
            currentArticle?.let {
                rssService.get().markAsStarred(articleId = it.id, isStarred = isStarred)
            }
        }
    }

    private fun setLoading() {
        _readerState.update { it.copy(content = ReaderState.Loading) }
    }

    fun ReaderState.prefetchArticleId(): ReaderState {
        val items = articleListUseCase.itemSnapshotList
        val currentId = currentArticle?.id
        val index =
            items.indexOfFirst { item ->
                item is ArticleFlowItem.Article && item.articleWithFeed.article.id == currentId
            }
        var previousArticle: ReaderState.PrefetchResult? = null
        var nextArticle: ReaderState.PrefetchResult? = null

        if (index != -1 || currentId == null) {
            val prevIterator = items.listIterator(index)
            while (prevIterator.hasPrevious()) {
                val previousIndex = prevIterator.previousIndex()
                val prev = prevIterator.previous()
                if (prev is ArticleFlowItem.Article) {
                    previousArticle =
                        ReaderState.PrefetchResult(
                            articleId = prev.articleWithFeed.article.id,
                            index = previousIndex,
                        )
                    break
                }
            }
            val nextIterator = items.listIterator(index + 1)
            while (nextIterator.hasNext()) {
                val nextIndex = nextIterator.nextIndex()
                val next = nextIterator.next()
                if (
                    next is ArticleFlowItem.Article && next.articleWithFeed.article.id != currentId
                ) {
                    nextArticle =
                        ReaderState.PrefetchResult(
                            articleId = next.articleWithFeed.article.id,
                            index = nextIndex,
                        )
                    break
                }
            }
        }

        Timber.d("$previousArticle, $nextArticle, $listIndex")
        return copy(nextArticle = nextArticle, previousArticle = previousArticle, listIndex = index)
    }

    fun downloadImage(
        url: String,
        onSuccess: (Uri) -> Unit = {},
        onFailure: (Throwable) -> Unit = {},
    ) {
        viewModelScope.launch {
            imageDownloader.downloadImage(url).onSuccess(onSuccess).onFailure(onFailure)
        }
    }

    fun summarizeCurrentArticle() {
        requestAiSummary(SummaryTrigger.MANUAL)
    }

    fun autoSummarizeCurrentArticle() {
        requestAiSummary(SummaryTrigger.AUTO)
    }

    fun translateCurrentArticle() {
        requestTranslation(TranslationTrigger.MANUAL)
    }

    fun autoTranslateCurrentArticle() {
        requestTranslation(TranslationTrigger.AUTO)
    }

    private fun requestAiSummary(trigger: SummaryTrigger) {
        if (readingUiState.value.isAiSummaryLoading) return
        viewModelScope.launch {
            val articleId = currentArticle?.id ?: return@launch
            val currentState = readingUiState.value
            val articleContent =
                readerStateStateFlow.value.content.text?.takeIf { it.isNotBlank() }
                    ?: currentArticle?.rawDescription
                    ?: ""
            val settings = settingsProvider.settings
            val keepInlineVisible = currentState.shouldRenderAiSummaryInline
            val keepInlineExpanded = keepInlineVisible && currentState.isAiSummaryExpanded
            val isAutoTrigger = trigger == SummaryTrigger.AUTO

            _readingUiState.update {
                it.copy(
                    isAiSummaryLoading = true,
                    isAiSummaryInlineLoading = keepInlineExpanded,
                    aiSummaryError = null,
                    isAiSummaryExpanded = keepInlineExpanded,
                    shouldShowAiSummaryReadyPrompt = false,
                    hasAutoAiSummaryAttempted = it.hasAutoAiSummaryAttempted || isAutoTrigger,
                )
            }

            if (settings.aiApiKey.value.isEmpty() || settings.aiBaseUrl.value.isEmpty()) {
                _readingUiState.update {
                    it.copy(
                        isAiSummaryLoading = false,
                        isAiSummaryInlineLoading = false,
                        aiSummaryError =
                            if (isAutoTrigger) null else "Please configure API URL and key first",
                    )
                }
                return@launch
            }

            val result = aiSummaryRepository.summarizeArticle(
                baseUrl = settings.aiBaseUrl.value,
                apiKey = settings.aiApiKey.value,
                model = settings.aiModel.value.ifEmpty { "gpt-3.5-turbo" },
                prompt = settings.aiSummarizationPrompt.value.ifEmpty {
                    "Please provide a concise summary of the following article in 3-5 bullet points:\n\n"
                },
                articleContent = articleContent
            )

            when (result) {
                is me.ash.reader.infrastructure.net.ApiResult.Success -> {
                    articleDao.updateAiSummary(articleId = articleId, aiSummary = result.data)
                    val updatedArticleWithFeed =
                        rssService.get().findArticleById(articleId)
                            ?: readingUiState.value.articleWithFeed?.copy(
                                article =
                                    (currentArticle ?: return@launch).copy(aiSummary = result.data)
                            )
                    _readingUiState.update {
                        val shouldExpandInlineSummary = isAiSummaryCardVisible.value
                        it.copy(
                            articleWithFeed = updatedArticleWithFeed,
                            aiSummary = result.data,
                            isAiSummaryLoading = false,
                            isAiSummaryInlineLoading = false,
                            aiSummaryError = null,
                            isAiSummaryExpanded = shouldExpandInlineSummary,
                            shouldRenderAiSummaryInline = shouldExpandInlineSummary,
                            shouldShowAiSummaryReadyPrompt = !shouldExpandInlineSummary,
                        )
                    }
                }
                is me.ash.reader.infrastructure.net.ApiResult.BizError -> {
                    _readingUiState.update {
                        it.copy(
                            isAiSummaryLoading = false,
                            isAiSummaryInlineLoading = false,
                            aiSummaryError =
                                if (isAutoTrigger) null
                                else result.exception.message ?: "Business error",
                        )
                    }
                }
                is me.ash.reader.infrastructure.net.ApiResult.NetworkError -> {
                    _readingUiState.update {
                        it.copy(
                            isAiSummaryLoading = false,
                            isAiSummaryInlineLoading = false,
                            aiSummaryError =
                                if (isAutoTrigger) null
                                else result.exception.message ?: "Network error",
                        )
                    }
                }
                is me.ash.reader.infrastructure.net.ApiResult.UnknownError -> {
                    _readingUiState.update {
                        it.copy(
                            isAiSummaryLoading = false,
                            isAiSummaryInlineLoading = false,
                            aiSummaryError =
                                if (isAutoTrigger) null
                                else result.throwable.message ?: "Unknown error",
                        )
                    }
                }
            }
        }
    }

    private fun requestTranslation(trigger: TranslationTrigger) {
        if (translationJob?.isActive == true || readingUiState.value.isTranslationLoading) return
        val job =
            viewModelScope.launch {
            if (currentFeed?.isTranslationEnabled != true) return@launch
            val articleId = currentArticle?.id ?: return@launch
            val article = currentArticle ?: return@launch
            val content = readerStateStateFlow.value.content.text?.takeIf { it.isNotBlank() }
                ?: article.rawDescription
            val blocks = ArticleContentBlockParser.parse(content = content, baseUrl = article.link)
            val eligibleBlocks = ArticleContentBlockParser.translationSourcePayload(blocks)
            if (eligibleBlocks.isEmpty()) return@launch
            val translatableCount = translatableBlockCount(blocks)
            val sourceHash = ArticleContentBlockParser.translationSourceHash(blocks)
            val isAutoTrigger = trigger == TranslationTrigger.AUTO
            val storedTranslations =
                if (article.translationSourceHash == sourceHash) {
                    decodeStoredTranslationBlocks(article.translationBlocksZh)
                } else {
                    emptyList()
                }
            val existingTranslations =
                selectTranslationsForCurrentBlocks(blocks = blocks, storedBlocks = storedTranslations)
            val storedExtraTranslations =
                selectExtraTranslations(blocks = blocks, storedBlocks = storedTranslations)
            val existingTranslatedCount =
                translatedBlockCount(blocks, existingTranslations.map { it.id }.toSet())

            if (
                isAutoTrigger &&
                    existingTranslatedCount == translatableCount
            ) {
                if (!isTranslationRequestCurrent(articleId)) return@launch
                _readingUiState.update {
                    it.copy(
                        translatedContentBlocks =
                            serializeTranslatedBlocks(storedExtraTranslations + existingTranslations),
                        shouldRenderTranslationInline = true,
                        hasAutoTranslationAttempted = true,
                        translatedBlockCount = existingTranslatedCount,
                        translatableBlockCount = translatableCount,
                    )
                }
                return@launch
            }

            if (!isTranslationRequestCurrent(articleId)) return@launch
            _readingUiState.update {
                it.copy(
                    isTranslationLoading = true,
                    isTranslationInlineLoading = it.shouldRenderTranslationInline,
                    translationError = null,
                    hasAutoTranslationAttempted = it.hasAutoTranslationAttempted || isAutoTrigger,
                    translatedBlockCount = existingTranslatedCount,
                    translatableBlockCount = translatableCount,
                )
            }

            val settings = settingsProvider.settings
            if (settings.aiApiKey.value.isEmpty() || settings.aiBaseUrl.value.isEmpty()) {
                _readingUiState.update {
                    it.copy(
                        isTranslationLoading = false,
                        isTranslationInlineLoading = false,
                        translationError =
                            if (isAutoTrigger) null else "Please configure API URL and key first",
                    )
                }
                return@launch
            }

            val accumulatedTranslations = existingTranslations.associateBy { it.id }.toMutableMap()
            while (true) {
                if (!isTranslationRequestCurrent(articleId)) return@launch
                val nextBatch =
                    buildPrioritizedTranslationBatch(
                        blocks = blocks,
                        translatedBlockIds = accumulatedTranslations.keys,
                        preferredStartIndex = translationFocusIndex.value,
                    )
                if (nextBatch.isEmpty()) break

                when (
                    val result =
                        aiTranslationRepository.translateBlocks(
                            baseUrl = settings.aiBaseUrl.value,
                            apiKey = settings.aiApiKey.value,
                            model = settings.aiModel.value.ifEmpty { "gpt-3.5-turbo" },
                            prompt =
                                settings.aiTranslationPrompt.value.ifEmpty {
                                    DEFAULT_TRANSLATION_PROMPT
                                },
                            sourceBlocks = nextBatch,
                        )
                ) {
                    is me.ash.reader.infrastructure.net.ApiResult.Success -> {
                        if (!isTranslationRequestCurrent(articleId)) return@launch
                        result.data.forEach { accumulatedTranslations[it.id] = it }
                        val mergedTranslations =
                            storedExtraTranslations + blocks.mapNotNull { block -> accumulatedTranslations[block.id] }
                        val serializedTranslation = serializeTranslatedBlocks(mergedTranslations)
                            ?: return@launch
                        val translatedCount =
                            translatedBlockCount(blocks, accumulatedTranslations.keys)
                        articleDao.updateTranslation(
                            articleId = articleId,
                            translationBlocksZh = serializedTranslation,
                            translationSourceHash = sourceHash,
                        )
                        val updatedArticle =
                            (readingUiState.value.articleWithFeed?.article ?: article).copy(
                                translationBlocksZh = serializedTranslation,
                                translationSourceHash = sourceHash,
                            )
                        _readingUiState.update {
                            if (it.articleWithFeed?.article?.id != articleId) {
                                return@update it
                            }
                            it.copy(
                                articleWithFeed =
                                    it.articleWithFeed?.copy(article = updatedArticle),
                                translatedContentBlocks = serializedTranslation,
                                shouldRenderTranslationInline = true,
                                translationError = null,
                                translatedBlockCount = translatedCount,
                                translatableBlockCount = translatableCount,
                            )
                        }
                    }
                    is me.ash.reader.infrastructure.net.ApiResult.BizError -> {
                        _readingUiState.update {
                            it.copy(
                                isTranslationLoading = false,
                                isTranslationInlineLoading = false,
                                translationError = result.exception.message ?: "Business error",
                            )
                        }
                        return@launch
                    }
                    is me.ash.reader.infrastructure.net.ApiResult.NetworkError -> {
                        _readingUiState.update {
                            it.copy(
                                isTranslationLoading = false,
                                isTranslationInlineLoading = false,
                                translationError = result.exception.message ?: "Network error",
                            )
                        }
                        return@launch
                    }
                    is me.ash.reader.infrastructure.net.ApiResult.UnknownError -> {
                        _readingUiState.update {
                            it.copy(
                                isTranslationLoading = false,
                                isTranslationInlineLoading = false,
                                translationError = result.throwable.message ?: "Unknown error",
                            )
                        }
                        return@launch
                    }
                }
            }
            if (!isTranslationRequestCurrent(articleId)) return@launch
            val serializedTranslation =
                serializeTranslatedBlocks(
                    storedExtraTranslations + blocks.mapNotNull { block -> accumulatedTranslations[block.id] }
                )
            _readingUiState.update {
                it.copy(
                    isTranslationLoading = false,
                    isTranslationInlineLoading = false,
                    translationError = null,
                    translatedContentBlocks = serializedTranslation,
                    shouldRenderTranslationInline = accumulatedTranslations.isNotEmpty(),
                    translatedBlockCount = translatedBlockCount(blocks, accumulatedTranslations.keys),
                    translatableBlockCount = translatableCount,
                )
            }
        }
        translationJob = job
        job.invokeOnCompletion {
            if (translationJob === job) {
                translationJob = null
            }
        }
    }

    private fun syncTranslationStateForContent(content: String) {
        if (currentFeed?.isTranslationEnabled != true) {
            _readingUiState.update {
                it.copy(
                    translatedContentBlocks = null,
                    shouldRenderTranslationInline = false,
                    translatedBlockCount = 0,
                    translatableBlockCount = 0,
                )
            }
            return
        }
        val article = currentArticle ?: return
        val translationState = translationContentStateForContent(content = content, article = article)
        _readingUiState.update {
            it.copy(
                translatedContentBlocks = translationState.payload,
                shouldRenderTranslationInline = translationState.payload != null,
                translatedBlockCount = translationState.translatedBlockCount,
                translatableBlockCount = translationState.translatableBlockCount,
            )
        }
    }

    private fun translationContentStateForContent(
        content: String,
        article: Article,
    ): TranslationContentState {
        val blocks = ArticleContentBlockParser.parse(content = content, baseUrl = article.link)
        val translatableCount = translatableBlockCount(blocks)
        if (
            article.translationBlocksZh.isNullOrBlank() || article.translationSourceHash.isNullOrBlank()
        ) {
            return TranslationContentState(
                payload = null,
                translatedBlockCount = 0,
                translatableBlockCount = translatableCount,
            )
        }
        val sourceHash = ArticleContentBlockParser.translationSourceHash(blocks)
        if (article.translationSourceHash != sourceHash) {
            return TranslationContentState(
                payload = null,
                translatedBlockCount = 0,
                translatableBlockCount = translatableCount,
            )
        }
        val storedBlocks = decodeStoredTranslationBlocks(article.translationBlocksZh)
        val storedTranslations =
            selectTranslationsForCurrentBlocks(blocks = blocks, storedBlocks = storedBlocks)
        val storedExtraTranslations =
            selectExtraTranslations(blocks = blocks, storedBlocks = storedBlocks)
        val translatedCount =
            translatedBlockCount(blocks, storedTranslations.map { it.id }.toSet())
        return TranslationContentState(
            payload = serializeTranslatedBlocks(storedExtraTranslations + storedTranslations),
            translatedBlockCount = translatedCount,
            translatableBlockCount = translatableCount,
        )
    }

    fun toggleAiSummaryExpanded() {
        if (readingUiState.value.aiSummary == null && readingUiState.value.isAiSummaryLoading) {
            return
        }
        if (readingUiState.value.aiSummary == null && !readingUiState.value.isAiSummaryLoading) {
            requestAiSummary(SummaryTrigger.MANUAL)
            return
        }
        _readingUiState.update {
            it.copy(isAiSummaryExpanded = !it.isAiSummaryExpanded)
        }
    }

    fun showAiSummaryFromPrompt() {
        _readingUiState.update {
            it.copy(
                shouldShowAiSummaryReadyPrompt = false,
                shouldRenderAiSummaryInline = true,
                isAiSummaryExpanded = true,
            )
        }
    }

    fun clearHiddenAiSummaryError() {
        _readingUiState.update {
            if (it.shouldRenderAiSummaryInline) it else it.copy(aiSummaryError = null)
        }
    }

    fun clearHiddenTranslationError() {
        _readingUiState.update {
            if (it.shouldRenderTranslationInline) it else it.copy(translationError = null)
        }
    }

    fun clearTranslationError() {
        _readingUiState.update { it.copy(translationError = null) }
    }

    fun updateTranslationFocusIndex(index: Int) {
        translationFocusIndex.value = index.coerceAtLeast(0)
    }

    fun updateListTranslationTargets(feed: Feed?, articleIds: List<String>) {
        if (feed?.isTranslationEnabled != true || feed.isBrowser) {
            clearListTranslationTargets(cancelActive = true)
            return
        }
        pendingListTranslationArticleIds.clear()
        pendingListTranslationArticleIds.addAll(
            articleIds.distinct().filter { it !in activeListTranslationArticleIds }
        )
        ensureListTranslationJobs()
    }

    fun updateAiSummaryCardVisible(isVisible: Boolean) {
        isAiSummaryCardVisible.value = isVisible
    }

    private fun cancelTranslationJob() {
        translationJob?.cancel()
        translationJob = null
    }

    private fun clearListTranslationTargets(cancelActive: Boolean) {
        pendingListTranslationArticleIds.clear()
        if (cancelActive) {
            listTranslationJobs.values.forEach { it.cancel() }
            listTranslationJobs.clear()
            activeListTranslationArticleIds.clear()
        }
    }

    private fun ensureListTranslationJobs() {
        while (
            pendingListTranslationArticleIds.isNotEmpty() &&
                activeListTranslationArticleIds.size < MAX_LIST_TRANSLATION_CONCURRENCY
        ) {
            val nextArticleId = pendingListTranslationArticleIds.firstOrNull() ?: break
            pendingListTranslationArticleIds.remove(nextArticleId)
            activeListTranslationArticleIds += nextArticleId
            val job =
                viewModelScope.launch {
                    translateArticleFromList(nextArticleId)
                }
            listTranslationJobs[nextArticleId] = job
            job.invokeOnCompletion {
                listTranslationJobs.remove(nextArticleId)
                activeListTranslationArticleIds.remove(nextArticleId)
                ensureListTranslationJobs()
            }
        }
    }

    private fun isTranslationRequestCurrent(articleId: String): Boolean {
        return currentArticle?.id == articleId &&
            readerStateStateFlow.value.articleId == articleId &&
            readingUiState.value.articleWithFeed?.article?.id == articleId
    }

    private fun serializeTranslatedBlocks(
        blocks: List<me.ash.reader.domain.repository.TranslatedArticleBlock>,
    ): String? = blocks.takeIf { it.isNotEmpty() }?.let { Gson().toJson(it) }

    private suspend fun translateArticleFromList(articleId: String) {
        if (currentArticle?.id == articleId && (translationJob?.isActive == true || readingUiState.value.isTranslationLoading)) {
            return
        }
        val articleWithFeed = rssService.get().findArticleById(articleId) ?: return
        if (!articleWithFeed.feed.isTranslationEnabled || articleWithFeed.feed.isBrowser) return

        val settings = settingsProvider.settings
        if (settings.aiApiKey.value.isEmpty() || settings.aiBaseUrl.value.isEmpty()) return

        val content =
            if (articleWithFeed.feed.isFullContent) {
                readerCacheHelper.readFullContent(articleId).getOrNull()?.takeIf { it.isNotBlank() }
            } else {
                articleWithFeed.article.rawDescription
            } ?: return

        val blocks =
            ArticleContentBlockParser.parse(
                content = content,
                baseUrl = articleWithFeed.article.link,
            )
        val eligibleBlocks = ArticleContentBlockParser.translationSourcePayload(blocks)
        if (eligibleBlocks.isEmpty()) return

        val sourceHash = ArticleContentBlockParser.translationSourceHash(blocks)
        val existingTranslations =
            if (articleWithFeed.article.translationSourceHash == sourceHash) {
                decodeStoredTranslationBlocks(articleWithFeed.article.translationBlocksZh)
            } else {
                emptyList()
            }

        val nextBatch =
            buildListTranslationSourceBlocks(
                articleTitle = articleWithFeed.article.title,
                blocks = blocks,
            ).filter { sourceBlock ->
                existingTranslations.none { it.id == sourceBlock.id }
            }
        if (nextBatch.isEmpty()) return

        when (
            val result =
                aiTranslationRepository.translateBlocks(
                    baseUrl = settings.aiBaseUrl.value,
                    apiKey = settings.aiApiKey.value,
                    model = settings.aiModel.value.ifEmpty { "gpt-3.5-turbo" },
                    prompt =
                        settings.aiTranslationPrompt.value.ifEmpty {
                            DEFAULT_TRANSLATION_PROMPT
                        },
                    sourceBlocks = nextBatch,
                )
        ) {
            is me.ash.reader.infrastructure.net.ApiResult.Success -> {
                val mergedTranslations =
                    (existingTranslations + result.data)
                        .associateBy { it.id }
                        .values
                        .toList()
                val serializedTranslation = serializeTranslatedBlocks(mergedTranslations) ?: return
                articleDao.updateTranslation(
                    articleId = articleId,
                    translationBlocksZh = serializedTranslation,
                    translationSourceHash = sourceHash,
                )
            }
            else -> return
        }
    }
}

data class FlowUiState(val pagerData: PagerData, val nextFilterState: FilterState? = null)

data class ReadingUiState(
    val articleWithFeed: ArticleWithFeed? = null,
    val isUnread: Boolean = false,
    val isStarred: Boolean = false,
    val aiSummary: String? = null,
    val isAiSummaryLoading: Boolean = false,
    val isAiSummaryInlineLoading: Boolean = false,
    val aiSummaryError: String? = null,
    val isAiSummaryExpanded: Boolean = false,
    val shouldRenderAiSummaryInline: Boolean = false,
    val shouldShowAiSummaryReadyPrompt: Boolean = false,
    val hasAutoAiSummaryAttempted: Boolean = false,
    val translatedContentBlocks: String? = null,
    val isTranslationLoading: Boolean = false,
    val isTranslationInlineLoading: Boolean = false,
    val translationError: String? = null,
    val shouldRenderTranslationInline: Boolean = false,
    val hasAutoTranslationAttempted: Boolean = false,
    val translatedBlockCount: Int = 0,
    val translatableBlockCount: Int = 0,
) {
    val isAiSummaryVisible: Boolean
        get() =
            shouldRenderAiSummaryInline &&
                (aiSummary != null || isAiSummaryInlineLoading || aiSummaryError != null)

    val shouldAutoGenerateAiSummary: Boolean
        get() = aiSummary == null && !hasAutoAiSummaryAttempted && !isAiSummaryLoading

    val isTranslationVisible: Boolean
        get() =
            shouldRenderTranslationInline &&
                (translatedContentBlocks != null ||
                    isTranslationInlineLoading ||
                    translationError != null)

    val shouldAutoGenerateTranslation: Boolean
        get() =
            translatableBlockCount > 0 &&
                translatedBlockCount < translatableBlockCount &&
                !hasAutoTranslationAttempted &&
                !isTranslationLoading
}

data class ReaderState(
    val articleId: String? = null,
    val feedName: String = "",
    val title: String? = null,
    val author: String? = null,
    val link: String? = null,
    val publishedDate: Date = Date(0L),
    val content: ContentState = Loading,
    val listIndex: Int? = null,
    val nextArticle: PrefetchResult? = null,
    val previousArticle: PrefetchResult? = null,
) {
    data class PrefetchResult(val articleId: String, val index: Int)

    sealed interface ContentState {
        val text: String?
            get() {
                return when (this) {
                    is Description -> content
                    is Error -> message
                    is FullContent -> content
                    Loading -> null
                }
            }
    }

    data class FullContent(val content: String) : ContentState

    data class Description(val content: String) : ContentState

    data class Error(val message: String) : ContentState

    data object Loading : ContentState
}
