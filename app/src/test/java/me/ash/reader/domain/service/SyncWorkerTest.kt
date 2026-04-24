package me.ash.reader.domain.service

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncWorkerTest {

    @Test
    fun postSyncWorkNameScopesByAccount() {
        assertEquals("POST_SYNC_WORK:7", SyncWorker.postSyncWorkName(7))
    }
}
