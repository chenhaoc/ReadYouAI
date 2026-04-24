package me.ash.reader.domain.service

import org.junit.Assert.assertEquals
import org.junit.Test

class AiSummaryPrecomputeWorkerTest {

    @Test
    fun retryDelayMillisUsesExponentialBackoff() {
        assertEquals(15 * 60 * 1000L, AiSummaryPrecomputeWorker.retryDelayMillis(1))
        assertEquals(30 * 60 * 1000L, AiSummaryPrecomputeWorker.retryDelayMillis(2))
        assertEquals(60 * 60 * 1000L, AiSummaryPrecomputeWorker.retryDelayMillis(3))
    }

    @Test
    fun retryDelayMillisTreatsNonPositiveAttemptAsFirstAttempt() {
        assertEquals(15 * 60 * 1000L, AiSummaryPrecomputeWorker.retryDelayMillis(0))
    }
}
