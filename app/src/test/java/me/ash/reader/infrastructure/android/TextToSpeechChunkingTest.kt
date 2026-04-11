package me.ash.reader.infrastructure.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextToSpeechChunkingTest {

    @Test
    fun splitSpeakableSegments_splits_long_single_paragraph_into_multiple_segments() {
        val paragraph = buildString {
            repeat(24) {
                append("这是一个用于测试朗读拖动优化的长句子。")
            }
        }

        val segments = splitSpeakableSegments(paragraph)

        assertTrue(segments.size > 1)
        assertEquals(paragraph.length, segments.sumOf(String::length))
    }
}
