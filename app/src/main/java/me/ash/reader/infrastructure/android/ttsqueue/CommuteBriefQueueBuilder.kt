package me.ash.reader.infrastructure.android.ttsqueue

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt
import me.ash.reader.domain.model.article.ArticleWithFeed
import me.ash.reader.domain.repository.AiSummaryRepository
import me.ash.reader.domain.repository.CommuteBriefRecommendationCandidate
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.service.AccountService
import me.ash.reader.infrastructure.android.htmlSegmentCharCounts
import me.ash.reader.infrastructure.net.ApiResult
import me.ash.reader.infrastructure.preference.SettingsProvider
import me.ash.reader.ui.page.home.reading.resolveAiCommuteBriefRecommendationPrompt

private const val DEFAULT_CANDIDATE_LIMIT = 200
private const val AI_RECOMMENDATION_CANDIDATE_MULTIPLIER = 3
private const val AI_RECOMMENDATION_MAX_CANDIDATES = 60
private const val MS_PER_MINUTE = 60_000L

data class CommuteBriefBuildResult(
    val items: List<TtsQueueItem>,
    val meta: TtsCommuteQueueMeta?,
    val hasSources: Boolean,
    val estimatedDurationMinutes: Int,
    val generationMode: TtsCommuteQueueGenerationMode = TtsCommuteQueueGenerationMode.NewestFirst,
    val aiRecommendationFallback: Boolean = false,
)

class CommuteBriefQueueBuilder @Inject constructor(
    private val articleDao: ArticleDao,
    private val accountService: AccountService,
    private val settingsProvider: SettingsProvider,
    private val aiSummaryRepository: AiSummaryRepository,
) {
    suspend fun build(
        generationMode: TtsCommuteQueueGenerationMode = TtsCommuteQueueGenerationMode.NewestFirst,
    ): CommuteBriefBuildResult {
        val settings = settingsProvider.settings
        val groupIds = settings.commuteBriefGroupIds.decodeIdList()
        val feedIds = settings.commuteBriefFeedIds.decodeIdList()
        if (groupIds.isEmpty() && feedIds.isEmpty()) {
            return CommuteBriefBuildResult(
                items = emptyList(),
                meta = null,
                hasSources = false,
                estimatedDurationMinutes = 0,
                generationMode = generationMode,
            )
        }

        val targetDurationMinutes = settings.commuteBriefDuration.minutes
        val targetDurationMs = targetDurationMinutes * MS_PER_MINUTE
        val candidates =
            articleDao
                .queryCommuteBriefCandidates(
                    accountId = accountService.getCurrentAccountId(),
                    groupIds = groupIds.ifEmpty { listOf(IMPOSSIBLE_ID) },
                    feedIds = feedIds.ifEmpty { listOf(IMPOSSIBLE_ID) },
                    limit = DEFAULT_CANDIDATE_LIMIT,
                )
                .mapNotNull { it.toCommuteBriefCandidate() }

        val selection =
            when (generationMode) {
                TtsCommuteQueueGenerationMode.NewestFirst ->
                    CommuteBriefSelection(candidates.selectForTargetDuration(targetDurationMs))

                TtsCommuteQueueGenerationMode.AiRecommended ->
                    recommendWithAi(
                        candidates = candidates,
                        targetDurationMs = targetDurationMs,
                        targetDurationMinutes = targetDurationMinutes,
                        baseUrl = settings.aiBaseUrl.value,
                        apiKey = settings.aiApiKey.value,
                        model = settings.aiModel.value.ifEmpty { "gpt-3.5-turbo" },
                        prompt = resolveAiCommuteBriefRecommendationPrompt(
                            settings.aiCommuteBriefRecommendationPrompt.value,
                        ),
                    )
            }

        val totalDurationMs = selection.candidates.sumOf { it.durationMs }
        val estimatedDurationMinutes =
            (totalDurationMs.toDouble() / MS_PER_MINUTE)
                .roundToInt()
                .coerceAtLeast(if (selection.candidates.isEmpty()) 0 else 1)
        return CommuteBriefBuildResult(
            items = selection.candidates.map { it.item },
            meta =
                TtsCommuteQueueMeta(
                    generatedAtMillis = System.currentTimeMillis(),
                    targetDurationMinutes = targetDurationMinutes,
                    estimatedDurationMinutes = estimatedDurationMinutes,
                    itemCount = selection.candidates.size,
                    generationMode = generationMode,
                ).takeIf { selection.candidates.isNotEmpty() },
            hasSources = true,
            estimatedDurationMinutes = estimatedDurationMinutes,
            generationMode = generationMode,
            aiRecommendationFallback = selection.aiRecommendationFallback,
        )
    }

    private suspend fun recommendWithAi(
        candidates: List<CommuteBriefCandidate>,
        targetDurationMs: Long,
        targetDurationMinutes: Int,
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
    ): CommuteBriefSelection {
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            return CommuteBriefSelection(
                candidates = candidates.selectForTargetDuration(targetDurationMs),
                aiRecommendationFallback = true,
            )
        }

        val recommendationCandidates =
            candidates
                .selectForTargetDuration(targetDurationMs * AI_RECOMMENDATION_CANDIDATE_MULTIPLIER)
                .take(AI_RECOMMENDATION_MAX_CANDIDATES)
        if (recommendationCandidates.isEmpty()) return CommuteBriefSelection(emptyList())

        val result =
            aiSummaryRepository.recommendCommuteBriefArticles(
                baseUrl = baseUrl,
                apiKey = apiKey,
                model = model,
                prompt = prompt,
                targetDurationMinutes = targetDurationMinutes,
                candidates = recommendationCandidates.map { it.toRecommendationCandidate() },
            )
        val recommendedIds = (result as? ApiResult.Success)?.data.orEmpty()
        if (recommendedIds.isEmpty()) {
            return CommuteBriefSelection(
                candidates = candidates.selectForTargetDuration(targetDurationMs),
                aiRecommendationFallback = true,
            )
        }

        val byId = recommendationCandidates.associateBy { it.articleId }
        val selected =
            recommendedIds
                .distinct()
                .mapNotNull(byId::get)
                .selectForTargetDuration(targetDurationMs)
                .toMutableList()
        val selectedIds = selected.map { it.articleId }.toMutableSet()
        if (selected.sumOf { it.durationMs } < targetDurationMs) {
            recommendationCandidates
                .filterNot { it.articleId in selectedIds }
                .forEach { candidate ->
                    if (selected.isNotEmpty() && selected.sumOf { it.durationMs } + candidate.durationMs > targetDurationMs) {
                        return@forEach
                    }
                    selected += candidate
                    selectedIds += candidate.articleId
                    if (selected.sumOf { it.durationMs } >= targetDurationMs) return@forEach
                }
        }
        return CommuteBriefSelection(candidates = selected)
    }

    private fun ArticleWithFeed.toCommuteBriefCandidate(): CommuteBriefCandidate? {
        val summary = article.aiSummary?.takeIf { it.isNotBlank() } ?: return null
        val summaryHtml =
            buildSummaryHtmlContent(
                title = article.title,
                feedName = feed.name,
                summary = summary,
            )
        val durationMs = charsToMs(htmlSegmentCharCounts(summaryHtml).sum())
        return CommuteBriefCandidate(
            articleId = article.id,
            item = toSummaryQueueItem(summaryHtml, durationMs),
            summary = summary,
            durationMs = durationMs,
            feedName = feed.name,
            title = article.title,
            publishedAt = formatCandidateDate(article.date),
        )
    }

    private fun CommuteBriefCandidate.toRecommendationCandidate(): CommuteBriefRecommendationCandidate =
        CommuteBriefRecommendationCandidate(
            articleId = articleId,
            title = title,
            feedName = feedName,
            publishedAt = publishedAt,
            summary = summary,
            estimatedDurationMinutes =
                (durationMs.toDouble() / MS_PER_MINUTE).roundToInt().coerceAtLeast(1),
        )

    private fun List<CommuteBriefCandidate>.selectForTargetDuration(targetDurationMs: Long): List<CommuteBriefCandidate> {
        var totalDurationMs = 0L
        val selected = mutableListOf<CommuteBriefCandidate>()
        forEach { candidate ->
            if (selected.isNotEmpty() && totalDurationMs + candidate.durationMs > targetDurationMs) {
                return@forEach
            }
            selected += candidate
            totalDurationMs += candidate.durationMs
            if (totalDurationMs >= targetDurationMs) return@forEach
        }
        return selected
    }

    private fun formatCandidateDate(date: Date): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(date)

    private fun String.decodeIdList(): List<String> =
        split('\n')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()

    companion object {
        private const val IMPOSSIBLE_ID = "__none__"
    }
}

private data class CommuteBriefCandidate(
    val articleId: String,
    val item: TtsQueueItem,
    val summary: String,
    val durationMs: Long,
    val feedName: String,
    val title: String,
    val publishedAt: String,
)

private data class CommuteBriefSelection(
    val candidates: List<CommuteBriefCandidate>,
    val aiRecommendationFallback: Boolean = false,
)
