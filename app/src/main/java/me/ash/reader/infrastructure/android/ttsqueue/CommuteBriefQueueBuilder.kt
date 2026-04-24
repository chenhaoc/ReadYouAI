package me.ash.reader.infrastructure.android.ttsqueue

import javax.inject.Inject
import kotlin.math.roundToInt
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.service.AccountService
import me.ash.reader.infrastructure.android.htmlSegmentCharCounts
import me.ash.reader.infrastructure.preference.SettingsProvider

private const val DEFAULT_CANDIDATE_LIMIT = 200
private const val MS_PER_MINUTE = 60_000L

data class CommuteBriefBuildResult(
    val items: List<TtsQueueItem>,
    val meta: TtsCommuteQueueMeta?,
    val hasSources: Boolean,
    val estimatedDurationMinutes: Int,
)

class CommuteBriefQueueBuilder @Inject constructor(
    private val articleDao: ArticleDao,
    private val accountService: AccountService,
    private val settingsProvider: SettingsProvider,
) {
    suspend fun build(): CommuteBriefBuildResult {
        val settings = settingsProvider.settings
        val groupIds = settings.commuteBriefGroupIds.decodeIdList()
        val feedIds = settings.commuteBriefFeedIds.decodeIdList()
        if (groupIds.isEmpty() && feedIds.isEmpty()) {
            return CommuteBriefBuildResult(
                items = emptyList(),
                meta = null,
                hasSources = false,
                estimatedDurationMinutes = 0,
            )
        }

        val targetDurationMinutes = settings.commuteBriefDuration.minutes
        val targetDurationMs = targetDurationMinutes * MS_PER_MINUTE
        var totalDurationMs = 0L
        val items = mutableListOf<TtsQueueItem>()
        articleDao
            .queryCommuteBriefCandidates(
                accountId = accountService.getCurrentAccountId(),
                groupIds = groupIds.ifEmpty { listOf(IMPOSSIBLE_ID) },
                feedIds = feedIds.ifEmpty { listOf(IMPOSSIBLE_ID) },
                limit = DEFAULT_CANDIDATE_LIMIT,
            )
            .forEach { articleWithFeed ->
                val summary = articleWithFeed.article.aiSummary?.takeIf { it.isNotBlank() } ?: return@forEach
                val summaryHtml =
                    buildSummaryHtmlContent(
                        title = articleWithFeed.article.title,
                        feedName = articleWithFeed.feed.name,
                        summary = summary,
                    )
                val durationMs = charsToMs(htmlSegmentCharCounts(summaryHtml).sum())
                if (items.isNotEmpty() && totalDurationMs + durationMs > targetDurationMs) {
                    return@forEach
                }
                items += articleWithFeed.toSummaryQueueItem(summaryHtml, durationMs)
                totalDurationMs += durationMs
                if (totalDurationMs >= targetDurationMs) return@forEach
            }

        val estimatedDurationMinutes = (totalDurationMs.toDouble() / MS_PER_MINUTE).roundToInt().coerceAtLeast(if (items.isEmpty()) 0 else 1)
        return CommuteBriefBuildResult(
            items = items,
            meta =
                TtsCommuteQueueMeta(
                    generatedAtMillis = System.currentTimeMillis(),
                    targetDurationMinutes = targetDurationMinutes,
                    estimatedDurationMinutes = estimatedDurationMinutes,
                    itemCount = items.size,
                ).takeIf { items.isNotEmpty() },
            hasSources = true,
            estimatedDurationMinutes = estimatedDurationMinutes,
        )
    }

    private fun String.decodeIdList(): List<String> =
        split('\n')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()

    companion object {
        private const val IMPOSSIBLE_ID = "__none__"
    }
}
