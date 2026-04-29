package me.ash.reader.domain.data

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.ash.reader.infrastructure.di.IODispatcher

data class PendingRemoteState(
    val readStatus: Map<String, Boolean> = emptyMap(),
    val starredStatus: Map<String, Boolean> = emptyMap(),
)

@Singleton
class PendingRemoteStateStore
@Inject
constructor(
    @ApplicationContext context: Context,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val gson = Gson()
    private val mutex = Mutex()
    private val rootDir = context.filesDir.resolve("pending_remote_state")

    suspend fun snapshot(accountId: Int): PendingRemoteState =
        withContext(ioDispatcher) { mutex.withLock { readState(accountId).toPendingState() } }

    suspend fun setReadStatus(accountId: Int, articleIds: Set<String>, isUnread: Boolean) {
        if (articleIds.isEmpty()) return
        mutate(accountId) { state ->
            articleIds.forEach { state.readStatus[it] = isUnread }
        }
    }

    suspend fun clearReadStatus(accountId: Int, articleIds: Set<String>) {
        if (articleIds.isEmpty()) return
        mutate(accountId) { state ->
            articleIds.forEach { state.readStatus.remove(it) }
        }
    }

    suspend fun setStarredStatus(accountId: Int, articleIds: Set<String>, isStarred: Boolean) {
        if (articleIds.isEmpty()) return
        mutate(accountId) { state ->
            articleIds.forEach { state.starredStatus[it] = isStarred }
        }
    }

    suspend fun clearStarredStatus(accountId: Int, articleIds: Set<String>) {
        if (articleIds.isEmpty()) return
        mutate(accountId) { state ->
            articleIds.forEach { state.starredStatus.remove(it) }
        }
    }

    private suspend fun mutate(accountId: Int, block: (MutablePendingRemoteState) -> Unit) {
        withContext(ioDispatcher) {
            mutex.withLock {
                try {
                    val state = readState(accountId)
                    block(state)
                    writeState(accountId, state)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun readState(accountId: Int): MutablePendingRemoteState {
        val file = stateFile(accountId)
        if (!file.exists() || !file.canRead()) return MutablePendingRemoteState()
        return runCatching {
                gson.fromJson(file.readText(), DiskPendingRemoteState::class.java)
            }
            .getOrNull()
            ?.toMutableState()
            ?: MutablePendingRemoteState()
    }

    private fun writeState(accountId: Int, state: MutablePendingRemoteState) {
        rootDir.mkdirs()
        val file = stateFile(accountId)
        if (state.readStatus.isEmpty() && state.starredStatus.isEmpty()) {
            if (file.exists()) file.delete()
            return
        }
        file.writeText(gson.toJson(state))
    }

    private fun stateFile(accountId: Int): File = rootDir.resolve("$accountId.json")

    private data class DiskPendingRemoteState(
        val readStatus: Map<String, Boolean>? = null,
        val starredStatus: Map<String, Boolean>? = null,
    ) {
        fun toMutableState(): MutablePendingRemoteState =
            MutablePendingRemoteState(
                readStatus = readStatus.orEmpty().toMutableMap(),
                starredStatus = starredStatus.orEmpty().toMutableMap(),
            )
    }

    private data class MutablePendingRemoteState(
        val readStatus: MutableMap<String, Boolean> = mutableMapOf(),
        val starredStatus: MutableMap<String, Boolean> = mutableMapOf(),
    ) {
        fun toPendingState(): PendingRemoteState =
            PendingRemoteState(
                readStatus = readStatus.toMap(),
                starredStatus = starredStatus.toMap(),
            )
    }
}
