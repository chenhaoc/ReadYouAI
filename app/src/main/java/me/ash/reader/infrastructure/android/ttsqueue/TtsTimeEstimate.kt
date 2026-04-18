package me.ash.reader.infrastructure.android.ttsqueue

import kotlin.math.ceil
import kotlin.math.roundToInt
import me.ash.reader.infrastructure.android.htmlSegmentCharCounts

/**
 * Estimate TTS playback duration from character counts.
 *
 * Typical TTS speaking rate:
 *  - Chinese: ~4 characters/second -> 250 ms/char
 *  - English: ~3 words/second ~= ~15 chars/second -> ~67 ms/char
 *  - Mixed content averages out around ~5-6 chars/second -> ~180 ms/char
 *
 * We use 180 ms/char as a reasonable middle ground. This is only an estimate;
 * the actual speed depends on the TTS engine, language, and speaking rate setting.
 */
private const val MS_PER_CHAR = 180L
private const val CHARS_PER_MINUTE_READING = 500.0
private const val MS_PER_MINUTE = 60_000.0

data class TtsPlaybackDurationEstimate(
    val currentMs: Long,
    val totalMs: Long,
)

data class TtsReadingStats(
    val charCount: Int,
    val readingMinutes: Int,
    val audioMinutes: Int,
)

/**
 * Convert a character count to estimated playback milliseconds.
 */
fun charsToMs(chars: Int): Long = chars.toLong() * MS_PER_CHAR

fun segmentCharCountsToDurationEstimate(
    currentSegmentIndex: Int,
    segmentCharCounts: List<Int>,
): TtsPlaybackDurationEstimate? {
    val totalChars = segmentCharCounts.sum()
    if (totalChars <= 0) return null

    val consumedChars =
        segmentCharCounts
            .take(currentSegmentIndex.coerceAtLeast(0))
            .sum()

    return TtsPlaybackDurationEstimate(
        currentMs = charsToMs(consumedChars),
        totalMs = charsToMs(totalChars),
    )
}

fun estimateReadingStats(
    rawDescription: String,
    shortDescription: String,
    title: String,
): TtsReadingStats? {
    val playableHtml = resolvePlayableHtmlContent(rawDescription, shortDescription, title) ?: return null
    return estimateReadingStatsFromCharCount(htmlSegmentCharCounts(playableHtml).sum())
}

fun estimateReadingStatsFromCharCount(charCount: Int): TtsReadingStats? {
    if (charCount <= 0) return null

    return TtsReadingStats(
        charCount = charCount,
        readingMinutes = ceil(charCount / CHARS_PER_MINUTE_READING).toInt().coerceAtLeast(1),
        audioMinutes = (charsToMs(charCount) / MS_PER_MINUTE).roundToInt().coerceAtLeast(1),
    )
}

/**
 * Convert a position in milliseconds back to the segment index in [segmentCharCounts].
 *
 * This is the inverse of the duration mapping: given a position in ms, find
 * which segment corresponds to that position.
 */
fun msToSegmentIndex(positionMs: Long, segmentCharCounts: List<Int>): Int {
    if (segmentCharCounts.isEmpty()) return 0
    val targetChars = (positionMs / MS_PER_CHAR).toInt()
    var accumulated = 0
    segmentCharCounts.forEachIndexed { index, count ->
        accumulated += count
        if (targetChars < accumulated) return index
    }
    return segmentCharCounts.lastIndex
}

/**
 * Format milliseconds into "mm:ss" display string.
 */
fun formatMsToTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
